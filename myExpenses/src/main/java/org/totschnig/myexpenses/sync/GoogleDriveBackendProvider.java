package org.totschnig.myexpenses.sync;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.annimon.stream.Collectors;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
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
import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Model;
import org.totschnig.myexpenses.sync.json.AccountMetaData;
import org.totschnig.myexpenses.sync.json.ChangeSet;
import org.totschnig.myexpenses.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class GoogleDriveBackendProvider extends AbstractSyncBackendProvider {
  private static final String KEY_LOCK_TOKEN = "lockToken";
  private static final String KEY_OWNED_BY_US = "ownedByUs";
  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_LAST_FAILED_SYNC = "lastFailedSync";
  private static final String KEY_SYNC_BACKOFF = "syncBackOff";
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
  private static final long LOCK_TIMEOUT = BuildConfig.DEBUG ? 60 * 1000 : 30 * 60 * 1000;
  private static final String TAG = GoogleDriveBackendProvider.class.getSimpleName();
  private String folderId;
  private DriveFolder baseFolder, accountFolder;

  private GoogleApiClient googleApiClient;
  private SharedPreferences sharedPreferences;

  GoogleDriveBackendProvider(Context context, android.accounts.Account account, AccountManager accountManager) {
    sharedPreferences = context.getSharedPreferences("google_drive_backend", 0);
    folderId = accountManager.getUserData(account, GenericAccountService.KEY_SYNC_PROVIDER_URL);
    googleApiClient = new GoogleApiClient.Builder(context)
        .addApi(Drive.API)
        .addScope(Drive.SCOPE_FILE)
        .build();
  }

  @Override
  public boolean setUp() {
   return setUp(false);
  }

  @SuppressLint("CommitPrefEdits")
  private boolean setUp(boolean requireSync) {
    long lastFailedSync = sharedPreferences.getLong(KEY_LAST_FAILED_SYNC, 0);
    long currentBackOff = sharedPreferences.getLong(KEY_SYNC_BACKOFF, 0);
    long now = System.currentTimeMillis();
    if (lastFailedSync != 0 && lastFailedSync + currentBackOff > now) {
      Log.e(TAG, String.format("Not syncing, waiting for another %d milliseconds", lastFailedSync + currentBackOff - now));
      return false;
    }
    if (googleApiClient.blockingConnect().isSuccess()) {
      Status status = Drive.DriveApi.requestSync(googleApiClient).await();
      if (!status.isSuccess()) {
        Log.e(TAG, "Sync failed with code " + status.getStatusCode());
        long newBackOff = Math.min(sharedPreferences.getLong(KEY_SYNC_BACKOFF, 5000) * 2, 3600000);
        Log.e(TAG, String.format("Backing off for %d milliseconds ", newBackOff));
        sharedPreferences.edit().putLong(KEY_LAST_FAILED_SYNC, now).putLong(KEY_SYNC_BACKOFF, newBackOff).commit();
        return !requireSync;
      } else {
        Log.i(TAG, "Sync succeeded");
        sharedPreferences.edit().remove(KEY_LAST_FAILED_SYNC).remove(KEY_SYNC_BACKOFF).apply();
        return true;
      }
    }
    return false;
  }

  @Override
  public void tearDown() {
    googleApiClient.disconnect();
  }

  @NonNull
  @Override
  protected InputStream getInputStreamForPicture(String relativeUri) throws IOException {
    Query query = new Query.Builder()
        .addFilter(Filters.eq(SearchableField.TITLE, relativeUri))
        .build();
    DriveApi.MetadataBufferResult metadataBufferResult =
        accountFolder.queryChildren(googleApiClient, query).await();
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
  protected void saveUri(String fileName, Uri uri) throws IOException {
    InputStream in = MyApplication.getInstance().getContentResolver()
        .openInputStream(uri);
    if (in == null) {
      throw new IOException("Could not read " + uri.toString());
    }
    saveBytes(fileName, FileCopyUtils.toByteArray(in),
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(getFileExtension(fileName)));
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
  void saveFileContents(String fileName, String fileContents, String mimeType) throws IOException {
    saveBytes(fileName, fileContents.getBytes(), mimeType);
  }

  private void saveBytes(String fileName, byte[] contents, String mimeType) throws IOException {
    DriveApi.DriveContentsResult driveContentsResult =
        Drive.DriveApi.newDriveContents(googleApiClient).await();
    if (!driveContentsResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to create new file contents");
    }
    DriveContents driveContents = driveContentsResult.getDriveContents();
    OutputStream outputStream = driveContents.getOutputStream();
    outputStream.write(contents);
    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
        .setTitle(fileName)
        .setMimeType(mimeType)
        .build();
    DriveFolder.DriveFileResult driveFileResult =
        accountFolder.createFile(googleApiClient, changeSet, driveContents).await();
    if (!driveFileResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to create file");
    }
  }

  @Override
  public boolean withAccount(Account account) {
    return requireAccountFolder(account);
  }

  @Override
  public boolean resetAccountData(String uuid) {
    try {
      return getExistingAccountFolder(uuid)
          .map(driveFolder -> driveFolder.trash(googleApiClient).await().isSuccess() &&
              Drive.DriveApi.requestSync(googleApiClient).await().getStatus().isSuccess())
          .orElse(true);
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public boolean lock() {
    DriveResource.MetadataResult metadataResult = accountFolder.getMetadata(googleApiClient).await();
    if (metadataResult.getStatus().isSuccess()) {
      Metadata metadata = metadataResult.getMetadata();
      if (!metadata.getCustomProperties().containsKey(LOCK_TOKEN_KEY) ||
          shouldOverrideLock(metadata.getCustomProperties().get(LOCK_TOKEN_KEY))) {
        MetadataChangeSet.Builder changeSetBuilder = new MetadataChangeSet.Builder();
        String lockToken = Model.generateUuid();
        changeSetBuilder.setCustomProperty(LOCK_TOKEN_KEY, lockToken);
        metadataResult = accountFolder.updateMetadata(googleApiClient, changeSetBuilder.build()).await();
        if (metadataResult.getStatus().isSuccess()) {
          saveLockTokenToPreferences(lockToken, System.currentTimeMillis(), true);
          return true;
        }
      }
    }
    return false;
  }

  private boolean shouldOverrideLock(String locktoken) {
    long now = System.currentTimeMillis();
    if (locktoken.equals(sharedPreferences.getString(KEY_LOCK_TOKEN, ""))) {
      return sharedPreferences.getBoolean(KEY_OWNED_BY_US, false) ||
          now - sharedPreferences.getLong(KEY_TIMESTAMP, 0) > LOCK_TIMEOUT;
    } else {
      saveLockTokenToPreferences(locktoken, now, false);
      return false;
    }
  }

  private void saveLockTokenToPreferences(String locktoken, long timestamp, boolean ownedByUs) {
    sharedPreferences.edit().putString(KEY_LOCK_TOKEN, locktoken).putLong(KEY_TIMESTAMP, timestamp)
        .putBoolean(KEY_OWNED_BY_US, ownedByUs).apply();
  }

  @Override
  public ChangeSet getChangeSetSince(long sequenceNumber, Context context) throws IOException {
    MetadataBuffer metadataBuffer = accountFolder.listChildren(googleApiClient).await().getMetadataBuffer();
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
  public boolean unlock() {
    MetadataChangeSet.Builder changeSetBuilder = new MetadataChangeSet.Builder();

    changeSetBuilder.deleteCustomProperty(LOCK_TOKEN_KEY);
    return accountFolder.updateMetadata(googleApiClient, changeSetBuilder.build())
        .await().getStatus().isSuccess();
  }

  @Override
  public List<AccountMetaData> getRemoteAccountList() throws IOException {
    List<AccountMetaData> result = null;
    if (setUp(false)) {
      if (requireBaseFolder()) {
        MetadataBuffer metadataBuffer = baseFolder.listChildren(googleApiClient).await().getMetadataBuffer();
        result = Stream.of(metadataBuffer)
            .map(this::getAccountMetaDataFromDriveMetadata)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
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
    return Optional.of(AccountMetaData.builder()
        .setType(customProperties.get(ACCOUNT_METADATA_TYPE_KEY))
        .setOpeningBalance(Long.parseLong(customProperties.get(ACCOUNT_METADATA_OPENING_BALANCE_KEY)))
        .setDescription(customProperties.get(ACCOUNT_METADATA_DESCRIPTION_KEY))
        .setColor(Integer.parseInt(customProperties.get(ACCOUNT_METADATA_COLOR_KEY)))
        .setCurrency(customProperties.get(ACCOUNT_METADATA_CURRENCY_KEY))
        .setUuid(uuid)
        .setLabel(metadata.getTitle()).build());
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

  private boolean requireAccountFolder(Account account) {
    boolean result = false;
    Optional<DriveFolder> driveFolderOptional;
    try {
      driveFolderOptional = getExistingAccountFolder(account.uuid);
    } catch (IOException e) {
      return false;
    }
    if (driveFolderOptional.isPresent()) {
      accountFolder = driveFolderOptional.get();
      result = true;
    } else {
      MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
          .setTitle(account.label)
          .setCustomProperty(ACCOUNT_METADATA_UUID_KEY, account.uuid)
          .setCustomProperty(ACCOUNT_METADATA_COLOR_KEY, String.valueOf(account.color))
          .setCustomProperty(ACCOUNT_METADATA_CURRENCY_KEY, account.currency.getCurrencyCode())
          // The total size of key string and value string of a custom property must be no more than 124 bytes (124 - ACCOUNT_METADATA_DESCRIPTION_KEY.length = 98
          .setCustomProperty(ACCOUNT_METADATA_DESCRIPTION_KEY, StringUtils.abbreviate(account.description, 98))
          .setCustomProperty(ACCOUNT_METADATA_TYPE_KEY, account.type.name())
          .setCustomProperty(ACCOUNT_METADATA_OPENING_BALANCE_KEY, String.valueOf(account.openingBalance.getAmountMinor()))
          .build();
      DriveFolder.DriveFolderResult driveFolderResult = baseFolder.createFolder(googleApiClient, changeSet).await();
      if (driveFolderResult.getStatus().isSuccess()) {
        accountFolder = driveFolderResult.getDriveFolder();
        createWarningFile();
        result = true;
      }
    }
    return result;
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
