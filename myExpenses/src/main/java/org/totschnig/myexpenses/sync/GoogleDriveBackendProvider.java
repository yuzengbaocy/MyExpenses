package org.totschnig.myexpenses.sync;

import android.accounts.AccountManager;
import android.content.Context;
import android.net.Uri;

import com.annimon.stream.Collectors;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.metadata.CustomPropertyKey;

import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.sync.json.AccountMetaData;
import org.totschnig.myexpenses.sync.json.ChangeSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class GoogleDriveBackendProvider extends AbstractSyncBackendProvider {
  private static final CustomPropertyKey ACCOUNT_METADATA_CURRENCY_KEY =
      new CustomPropertyKey("accountMetadataCurrency", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_COLOR_KEY =
      new CustomPropertyKey("accountMetadataColor", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_UUID_KEY =
      new CustomPropertyKey("accountMetadataUuid", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_OPENING_BALANCE_KEY =
      new CustomPropertyKey("accountMetadataOpeningBalance", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_DESCRIPTION_KEY =
      new CustomPropertyKey("accountMetadataDescription", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_TYPE_KEY =
      new CustomPropertyKey("accountMetadataType", CustomPropertyKey.PRIVATE);
  private String folderId;
  private DriveFolder baseFolder, accountFolder;

  private GoogleApiClient googleApiClient;

  GoogleDriveBackendProvider(Context context, android.accounts.Account account, AccountManager accountManager) {
    folderId = accountManager.getUserData(account, GenericAccountService.KEY_SYNC_PROVIDER_URL);
    googleApiClient = new GoogleApiClient.Builder(context)
        .addApi(Drive.API)
        .addScope(Drive.SCOPE_FILE)
        .build();
  }

  @Override

  protected InputStream getInputStreamForPicture(String relativeUri) throws IOException {
    return null;
  }

  @Override
  protected void saveUri(String fileName, Uri uri) throws IOException {

  }

  @Override
  protected long getLastSequence() throws IOException {
    MetadataBuffer metadataBuffer = accountFolder.listChildren(googleApiClient).await().getMetadataBuffer();
    Long result = Stream.of(metadataBuffer).filter(metadata -> isNewerJsonFile(0, metadata.getTitle()))
        .map(metadata -> getSequenceFromFileName(metadata.getTitle()))
        .max(this::compareInt)
        .orElse(0L);
    metadataBuffer.release();
    return result;
  }


  @Override
  void saveFileContents(String fileName, String fileContents) throws IOException {
    DriveApi.DriveContentsResult driveContentsResult =
        Drive.DriveApi.newDriveContents(googleApiClient).await();
    if (!driveContentsResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to create new file contents");
    }
    DriveContents driveContents = driveContentsResult.getDriveContents();
    OutputStream outputStream = driveContents.getOutputStream();
    outputStream.write(fileContents.getBytes());
    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
        .setTitle(fileName)
        .setMimeType(MIMETYPE_JSON)
        .build();
    DriveFolder.DriveFileResult driveFileResult =
        accountFolder.createFile(googleApiClient, changeSet, driveContents).await();
    if (!driveFileResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to create file");
    }
  }

  @Override
  public boolean withAccount(Account account) {
    boolean result = false;
    googleApiClient.blockingConnect();
    if (requireBaseFolder()) {
      result = requireAccountFolder(account);
    }
    if (!result) {
      googleApiClient.disconnect();
    }
    return result;
  }

  @Override
  public boolean resetAccountData(String uuid) {
    return false;
  }

  @Override
  public boolean lock() {
    return true;
  }

  @Override
  public ChangeSet getChangeSetSince(long sequenceNumber, Context context) throws IOException {
    MetadataBuffer metadataBuffer = accountFolder.listChildren(googleApiClient).await().getMetadataBuffer();
    ChangeSet result = merge(Stream.of(metadataBuffer).filter(metadata -> isNewerJsonFile(0, metadata.getTitle()))
        .map(this::getChangeSetFromMetadata))
        .orElse(ChangeSet.empty(sequenceNumber));
    metadataBuffer.release();
    return result;
  }

  private ChangeSet getChangeSetFromMetadata(Metadata metadata) {
    DriveApi.DriveContentsResult driveContentsResult =
        metadata.getDriveId().asDriveFile().open(googleApiClient, DriveFile.MODE_READ_ONLY, null).await();
    if (!driveContentsResult.getStatus().isSuccess()) {
      return ChangeSet.failed;
    }
    DriveContents driveContents = driveContentsResult.getDriveContents();
    try {
      return getChangeSetFromInputStream(getSequenceFromFileName(metadata.getTitle()),
          driveContents.getInputStream());
    } catch (IOException e) {
      return ChangeSet.failed;
    } finally {
      driveContents.discard(googleApiClient);
    }
  }

  @Override
  public boolean unlock() {
    googleApiClient.disconnect();
    return true;
  }

  @Override
  public List<AccountMetaData> getRemoteAccountList() throws IOException {
    googleApiClient.blockingConnect();
    if (!requireBaseFolder()) {
      return null;
    }
    MetadataBuffer metadataBuffer = baseFolder.listChildren(googleApiClient).await().getMetadataBuffer();
    List<AccountMetaData> accountMetaDataList = Stream.of(metadataBuffer)
        .map(this::getAccountMetaDataFromDriveMetadata)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
    metadataBuffer.release();
    googleApiClient.disconnect();
    return accountMetaDataList;
  }

  private Optional<AccountMetaData> getAccountMetaDataFromDriveMetadata(Metadata metadata) {
    Map<CustomPropertyKey, String> customProperties = metadata.getCustomProperties();
    String uuid = customProperties.get(ACCOUNT_METADATA_UUID_KEY);
    if (uuid == null) {
      return Optional.empty();
    }
    return Optional.of(AccountMetaData.builder()
        .setType(customProperties.get(ACCOUNT_METADATA_TYPE_KEY))
        .setOpeningBalance(Long.parseLong(customProperties.get(ACCOUNT_METADATA_OPENING_BALANCE_KEY)))
        .setDescription(customProperties.get(ACCOUNT_METADATA_DESCRIPTION_KEY))
        .setColor(Integer.parseInt(customProperties.get(ACCOUNT_METADATA_COLOR_KEY)))
        .setCurrency(customProperties.get(ACCOUNT_METADATA_CURRENCY_KEY))
        .setUuid(uuid)
        .setLabel(metadata.getTitle()).build());
  }

  private boolean requireAccountFolder(Account account)  {
    boolean result = false;
    MetadataBuffer metadataBuffer = baseFolder.listChildren(googleApiClient).await().getMetadataBuffer();
    Optional<DriveFolder> driveFolderOptional = Stream.of(metadataBuffer)
        .filter(metadata -> account.uuid.equals(metadata.getCustomProperties().get(ACCOUNT_METADATA_UUID_KEY)))
        .findFirst()
        .map(metadata -> metadata.getDriveId().asDriveFolder());
    if (driveFolderOptional.isPresent()) {
      accountFolder = driveFolderOptional.get();
      result = true;
    } else {
      MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
          .setTitle(account.label)
          .setCustomProperty(ACCOUNT_METADATA_UUID_KEY, account.uuid)
          .setCustomProperty(ACCOUNT_METADATA_COLOR_KEY, String.valueOf(account.color))
          .setCustomProperty(ACCOUNT_METADATA_CURRENCY_KEY, account.currency.getCurrencyCode())
          .setCustomProperty(ACCOUNT_METADATA_DESCRIPTION_KEY, account.description)
          .setCustomProperty(ACCOUNT_METADATA_TYPE_KEY, account.type.name())
          .setCustomProperty(ACCOUNT_METADATA_OPENING_BALANCE_KEY, String.valueOf(account.openingBalance.getAmountMinor()))
          .build();
      DriveFolder.DriveFolderResult driveFolderResult = baseFolder.createFolder(googleApiClient, changeSet).await();
      if (driveFolderResult.getStatus().isSuccess()) {
        accountFolder = driveFolderResult.getDriveFolder();
        result = true;
      }
    }
    metadataBuffer.release();
    return result;
  }

  private Optional<DriveFolder> getFolder(String folderId) {
    DriveApi.DriveIdResult driveIdResult = Drive.DriveApi.fetchDriveId(googleApiClient, folderId).await();
    if (!driveIdResult.getStatus().isSuccess()) {
      return Optional.empty();
    }
    DriveId driveId = driveIdResult.getDriveId();
    return Optional.of(driveId.asDriveFolder());
  }

  private boolean requireBaseFolder()  {
    if (baseFolder != null) {
      return true;
    }
    Optional<DriveFolder> driveFolderOptional = getFolder(folderId);
    if (driveFolderOptional.isPresent()) {
      baseFolder = driveFolderOptional.get();
      return true;
    }
    return false;
  }
}
