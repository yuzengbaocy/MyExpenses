package org.totschnig.myexpenses.sync;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import org.apache.commons.lang3.StringUtils;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.sync.json.AccountMetaData;
import org.totschnig.myexpenses.sync.json.ChangeSet;
import org.totschnig.myexpenses.util.AcraHelper;
import org.totschnig.myexpenses.util.FileCopyUtils;
import org.totschnig.myexpenses.util.Result;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import timber.log.Timber;

public class GoogleDriveBackendProvider extends AbstractSyncBackendProvider {
  private static final String KEY_LAST_FAILED_SYNC = "lastFailedSync";
  private static final String KEY_SYNC_BACKOFF = "syncBackOff";
  public static final String KEY_GOOGLE_ACCOUNT_EMAIL = "googleAccountEmail";
  private static final CustomPropertyKey ACCOUNT_METADATA_CURRENCY_KEY =
      new CustomPropertyKey("accountMetadataCurrency", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_COLOR_KEY =
      new CustomPropertyKey("accountMetadataColor", CustomPropertyKey.PRIVATE);
  public static final CustomPropertyKey ACCOUNT_METADATA_UUID_KEY =
      new CustomPropertyKey("accountMetadataUuid", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_OPENING_BALANCE_KEY =
      new CustomPropertyKey("accountMetadataOpeningBalance", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_DESCRIPTION_KEY =
      new CustomPropertyKey("accountMetadataDescription", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey ACCOUNT_METADATA_TYPE_KEY =
      new CustomPropertyKey("accountMetadataType", CustomPropertyKey.PRIVATE);
  private static final CustomPropertyKey LOCK_TOKEN_KEY =
      new CustomPropertyKey(KEY_LOCK_TOKEN, CustomPropertyKey.PRIVATE);
  private String folderId;
  private DriveFolder baseFolder, accountFolder;

  private GoogleApiClient googleApiClient;

  GoogleDriveBackendProvider(Context context, android.accounts.Account account, AccountManager accountManager) throws SyncParseException {
    super(context);
    folderId = accountManager.getUserData(account, GenericAccountService.KEY_SYNC_PROVIDER_URL);
    if (folderId == null) {
      throw new SyncParseException("Drive folder not set");
    }
    googleApiClient = new GoogleApiClient.Builder(context)
        .addApi(Drive.API)
        .addScope(Drive.SCOPE_FILE)
        .setAccountName(accountManager.getUserData(account, KEY_GOOGLE_ACCOUNT_EMAIL))
        .build();
  }

  @NonNull
  @Override
  protected String getSharedPreferencesName() {
    return "google_drive_backend";
  }

  private boolean requireSetup() {
    return setUp(null).success;
  }

  @Override
  public  Result setUp(String authToken) {
    long lastFailedSync = sharedPreferences.getLong(KEY_LAST_FAILED_SYNC, 0);
    long currentBackOff = sharedPreferences.getLong(KEY_SYNC_BACKOFF, 0);
    long now = System.currentTimeMillis();
    if (lastFailedSync != 0 && lastFailedSync + currentBackOff > now) {
      Timber.e("Not syncing, waiting for another %d milliseconds", lastFailedSync + currentBackOff - now);
      return Result.SUCCESS;
    }
    if (googleApiClient.isConnected()) {
      return setUpInternal(now);
    } else {
      ConnectionResult connectionResult = googleApiClient.blockingConnect();
      if (connectionResult.isSuccess()) {
        return setUpInternal(now);
      } else {
        Timber.e(connectionResult.getErrorMessage());
        return new Result(false, R.string.sync_io_error_cannot_connect, connectionResult.getResolution());
      }
    }
  }

  @SuppressLint("ApplySharedPref")
  private Result setUpInternal(long now) {
    Status status = Drive.DriveApi.requestSync(googleApiClient).await();
    if (!status.isSuccess()) {
      Timber.e("Sync failed with code %d", status.getStatusCode());
      long newBackOff = Math.min(sharedPreferences.getLong(KEY_SYNC_BACKOFF, 5000) * 2, 3600000);
      Timber.e("Backing off for %d milliseconds ", newBackOff);
      sharedPreferences.edit().putLong(KEY_LAST_FAILED_SYNC, now).putLong(KEY_SYNC_BACKOFF, newBackOff).commit();
      return Result.SUCCESS;
    } else {
      Timber.i("Sync succeeded");
      sharedPreferences.edit().remove(KEY_LAST_FAILED_SYNC).remove(KEY_SYNC_BACKOFF).apply();
      return Result.SUCCESS;
    }
  }

  @Override
  public void tearDown() {
    googleApiClient.disconnect();
  }

  @NonNull
  @Override
  protected InputStream getInputStreamForPicture(String relativeUri) throws IOException {
    return getInputStream(accountFolder, relativeUri);
  }

  @Override
  public InputStream getInputStreamForBackup(android.accounts.Account account, String backupFile) throws IOException {
    if (requireSetup()) {
      return getInputStream(getBackupFolder(), backupFile);
    }
    else {
      throw new IOException(getContext().getString(R.string.sync_io_error_cannot_connect));
    }
  }

  private InputStream getInputStream(DriveFolder folder, String title) throws IOException {
    Query query = new Query.Builder()
        .addFilter(Filters.eq(SearchableField.TITLE, title))
        .build();
    DriveApi.MetadataBufferResult metadataBufferResult =
        folder.queryChildren(googleApiClient, query).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Unable to find picture");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    if (metadataBuffer.getCount() != 1) {
      metadataBuffer.release();
      throw new IOException("Unable to find picture");
    }
    DriveApi.DriveContentsResult driveContentsResult = metadataBuffer.get(0).getDriveId()
        .asDriveFile().open(googleApiClient, DriveFile.MODE_READ_ONLY, null).await();
    if (!driveContentsResult.getStatus().isSuccess()) {
      metadataBuffer.release();
      throw new IOException("Unable to open picture");
    }
    return driveContentsResult.getDriveContents().getInputStream();
  }

  @Override
  protected void saveUriToAccountDir(String fileName, Uri uri) throws IOException {
    saveUriToFolder(fileName, uri, accountFolder);
  }

  private void saveUriToFolder(String fileName, Uri uri, DriveFolder driveFolder) throws IOException {
    InputStream in = MyApplication.getInstance().getContentResolver()
        .openInputStream(uri);
    if (in == null) {
      throw new IOException("Could not read " + uri.toString());
    }
    saveInputStream(fileName, in,
        getMimeType(fileName), driveFolder);
    in.close();
  }

  @Override
  public void storeBackup(Uri uri, String fileName) throws IOException {
    saveUriToFolder(fileName, uri, getBackupFolder());
  }

  @NonNull
  @Override
  public List<String> getStoredBackups(android.accounts.Account account) throws IOException {
    List<String> result = null;
    if (requireSetup()) {
      DriveApi.MetadataBufferResult metadataBufferResult = getBackupFolder().listChildren(googleApiClient).await();
      if (!metadataBufferResult.getStatus().isSuccess()) {
        throw new IOException("Error while trying to get backup list");
      }
      MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
      result = Stream.of(metadataBuffer)
          .map(Metadata::getTitle)
          .toList();
      metadataBuffer.release();
    } else {
      result = new ArrayList<>();
    }
    tearDown();
    return result;
  }

  @Override
  protected long getLastSequence(long start) throws IOException {
    DriveApi.MetadataBufferResult metadataBufferResult = accountFolder.listChildren(googleApiClient).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to get last sequence");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    Long result = Stream.of(metadataBuffer)
        .filter(metadata -> isNewerJsonFile(start, metadata.getTitle()))
        .map(metadata -> getSequenceFromFileName(metadata.getTitle()))
        .max(this::compareInt)
        .orElse(start);
    metadataBuffer.release();
    return result;
  }

  @Override
  void saveFileContents(String fileName, String fileContents, String mimeType) throws IOException {
    saveInputStream(fileName, new ByteArrayInputStream(fileContents.getBytes()), mimeType, accountFolder);
  }

  @Override
  protected String getExistingLockToken() throws IOException {
    DriveResource.MetadataResult metadataResult = accountFolder.getMetadata(googleApiClient).await();
    if (metadataResult.getStatus().isSuccess()) {
      return metadataResult.getMetadata().getCustomProperties().get(LOCK_TOKEN_KEY);
    } else {
      throw new IOException("Failure while getting metadata");
    }
  }

  @Override
  protected void writeLockToken(String lockToken) throws IOException {
    MetadataChangeSet.Builder changeSetBuilder = new MetadataChangeSet.Builder();
    changeSetBuilder.setCustomProperty(LOCK_TOKEN_KEY, lockToken);
    DriveResource.MetadataResult metadataResult = accountFolder.updateMetadata(googleApiClient, changeSetBuilder.build()).await();
    if (!metadataResult.getStatus().isSuccess()) {
      throw new IOException("Error while writing lock token");
    }
  }

  private void saveInputStream(String fileName, InputStream contents, String mimeType, DriveFolder driveFolder) throws IOException {
    DriveApi.DriveContentsResult driveContentsResult =
        Drive.DriveApi.newDriveContents(googleApiClient).await();
    if (!driveContentsResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to create new file contents");
    }
    DriveContents driveContents = driveContentsResult.getDriveContents();
    OutputStream outputStream = driveContents.getOutputStream();
    FileCopyUtils.copy(contents, outputStream);
    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
        .setTitle(fileName)
        .setMimeType(mimeType)
        .build();
    DriveFolder.DriveFileResult driveFileResult =
        driveFolder.createFile(googleApiClient, changeSet, driveContents).await();
    if (!driveFileResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to create file");
    }
    Timber.tag(SyncAdapter.TAG).i(driveFileResult.getStatus().toString());
  }

  @Override
  public void withAccount(Account account) throws IOException {
    setAccountUuid(account);
    requireAccountFolder(account);
  }

  @Override
  public void resetAccountData(String uuid) throws IOException {
    Optional<DriveFolder> existingAccountFolder = getExistingAccountFolder(uuid);
    if (existingAccountFolder.isPresent()) {
      if (!(existingAccountFolder.get().trash(googleApiClient).await().isSuccess() &&
          Drive.DriveApi.requestSync(googleApiClient).await().getStatus().isSuccess())) {
        throw new IOException("Error while reseting account data");
      }
    }
  }

  @Override
  public
  @NonNull
  ChangeSet getChangeSetSince(long sequenceNumber, Context context) throws IOException {
    DriveApi.MetadataBufferResult metadataBufferResult = accountFolder.listChildren(googleApiClient).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to get change set");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    ChangeSet result = merge(Stream.of(metadataBuffer)
        .filter(metadata -> isNewerJsonFile(sequenceNumber, metadata.getTitle()))
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
  public void unlock() throws IOException {
    MetadataChangeSet.Builder changeSetBuilder = new MetadataChangeSet.Builder();

    changeSetBuilder.deleteCustomProperty(LOCK_TOKEN_KEY);
    if (!accountFolder.updateMetadata(googleApiClient, changeSetBuilder.build())
        .await().getStatus().isSuccess()) {
      throw new IOException("Error while unlocking backend");
    }
  }

  @NonNull
  @Override
  public Stream<AccountMetaData> getRemoteAccountList(android.accounts.Account account) throws IOException {
    Stream<AccountMetaData> result = Stream.empty();
    if (requireSetup()) {
      if (requireBaseFolder()) {
        DriveApi.MetadataBufferResult metadataBufferResult = baseFolder.listChildren(googleApiClient).await();
        if (!metadataBufferResult.getStatus().isSuccess()) {
          throw new IOException("Error while trying to get account list");
        }
        MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
        List<AccountMetaData> accountMetaDataList = Stream.of(metadataBuffer)
            .map(this::getAccountMetaDataFromDriveMetadata)
            .filter(Optional::isPresent)
            .map(Optional::get).toList();
        result = Stream.of(accountMetaDataList);
        metadataBuffer.release();
      }
      tearDown();
    }
    return result;
  }

  private Optional<AccountMetaData> getAccountMetaDataFromDriveMetadata(Metadata metadata) {
    Map<CustomPropertyKey, String> customProperties = metadata.getCustomProperties();
    if (metadata.isTrashed()) {
      return Optional.empty();
    }
    String uuid = customProperties.get(ACCOUNT_METADATA_UUID_KEY);
    if (uuid == null) {
      return Optional.empty();
    }
    //TODO add default values
    return Optional.of(AccountMetaData.builder()
        .setType(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_TYPE_KEY, AccountType.CASH.name()))
        .setOpeningBalance(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_OPENING_BALANCE_KEY, 0L))
        .setDescription(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_DESCRIPTION_KEY, ""))
        .setColor(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_COLOR_KEY, Account.DEFAULT_COLOR))
        .setCurrency(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_CURRENCY_KEY,
            Currency.getInstance(Locale.getDefault()).getCurrencyCode()))
        .setUuid(uuid)
        .setLabel(metadata.getTitle()).build());
  }

  private String getPropertyWithDefault(Map<CustomPropertyKey, String> customProperties,
                                        CustomPropertyKey key,
                                        String defaultValue) {
    String result = customProperties.get(key);
    return result != null ? result : defaultValue;
  }

  private long getPropertyWithDefault(Map<CustomPropertyKey, String> customProperties,
                                      CustomPropertyKey key,
                                      long defaultValue) {
    String result = customProperties.get(key);
    return result != null ? Long.parseLong(result) : defaultValue;
  }

  private int getPropertyWithDefault(Map<CustomPropertyKey, String> customProperties,
                                     CustomPropertyKey key,
                                     int defaultValue) {
    String result = customProperties.get(key);
    return result != null ? Integer.parseInt(result) : defaultValue;
  }

  private DriveFolder getBackupFolder() throws IOException {
    if (!requireBaseFolder()) {
      throw new IOException("Base folder not available");
    }
    Query query = new Query.Builder()
        .addFilter(Filters.eq(SearchableField.TITLE, BACKUP_FOLDER_NAME))
        .build();
    DriveApi.MetadataBufferResult metadataBufferResult =
        baseFolder.queryChildren(googleApiClient, query).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Unable to query for backup folder");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    if (metadataBuffer.getCount() == 0) {
      metadataBuffer.release();
      MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
          .setTitle(BACKUP_FOLDER_NAME).build();
      DriveFolder.DriveFolderResult driveFolderResult = baseFolder.createFolder(googleApiClient, changeSet).await();
      if (!driveFolderResult.getStatus().isSuccess()) {
        throw new IOException("Unable to create backup folder");
      }
      return driveFolderResult.getDriveFolder();
    } else {
      DriveFolder result = metadataBuffer.get(0).getDriveId().asDriveFolder();
      metadataBuffer.release();
      return result;
    }
  }

  private Optional<DriveFolder> getExistingAccountFolder(String uuid) throws IOException {
    if (!requireBaseFolder()) {
      throw new IOException("Base folder not available");
    }
    DriveApi.MetadataBufferResult metadataBufferResult = baseFolder.listChildren(googleApiClient).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Unable to list children of base folder");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    Optional<DriveFolder> result = Stream.of(metadataBuffer)
        .filter(metadata -> uuid.equals(metadata.getCustomProperties().get(ACCOUNT_METADATA_UUID_KEY)) &&
            !metadata.isTrashed())
        .findFirst()
        .map(metadata -> metadata.getDriveId().asDriveFolder());
    metadataBuffer.release();
    return result;
  }

  private void requireAccountFolder(Account account) throws IOException {
    Optional<DriveFolder> driveFolderOptional;
    driveFolderOptional = getExistingAccountFolder(account.uuid);
    if (driveFolderOptional.isPresent()) {
      accountFolder = driveFolderOptional.get();
    } else {
      MetadataChangeSet.Builder builder = new MetadataChangeSet.Builder()
          .setTitle(account.label)
          .setCustomProperty(ACCOUNT_METADATA_UUID_KEY, account.uuid)
          .setCustomProperty(ACCOUNT_METADATA_COLOR_KEY, String.valueOf(account.color))
          .setCustomProperty(ACCOUNT_METADATA_CURRENCY_KEY, account.currency.getCurrencyCode())
          .setCustomProperty(ACCOUNT_METADATA_TYPE_KEY, account.getType().name())
          .setCustomProperty(ACCOUNT_METADATA_OPENING_BALANCE_KEY, String.valueOf(account.openingBalance.getAmountMinor()));
      try {
        // The total size of key string and value string of a custom property must be no more than 124 bytes (124 - ACCOUNT_METADATA_DESCRIPTION_KEY.length = 98
        builder.setCustomProperty(ACCOUNT_METADATA_DESCRIPTION_KEY, StringUtils.abbreviate(account.description, 98));
      } catch (IllegalArgumentException e) {
        HashMap<String, String> customData = new HashMap<>();
        customData.put("accountDescription", account.description);
        customData.put("accountDescriptionAbbreviated", StringUtils.abbreviate(account.description, 98));
        AcraHelper.report(e, customData);
      }
      MetadataChangeSet changeSet = builder.build();
      DriveFolder.DriveFolderResult driveFolderResult = baseFolder.createFolder(googleApiClient, changeSet).await();
      if (driveFolderResult.getStatus().isSuccess()) {
        accountFolder = driveFolderResult.getDriveFolder();
        createWarningFile();
      } else {
        throw new IOException("Error while creating account folder");
      }
    }
  }

  private boolean requireBaseFolder() {
    if (baseFolder != null) {
      return true;
    }
    DriveApi.DriveIdResult driveIdResult = Drive.DriveApi.fetchDriveId(googleApiClient, folderId).await();
    if (driveIdResult.getStatus().isSuccess()) {
      baseFolder = driveIdResult.getDriveId().asDriveFolder();
      return true;
    }
    return false;
  }
}
