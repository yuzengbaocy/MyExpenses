package org.totschnig.myexpenses.sync;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;

import com.annimon.stream.Exceptional;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.DriveStatusCodes;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.sync.json.AccountMetaData;
import org.totschnig.myexpenses.sync.json.ChangeSet;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;
import org.totschnig.myexpenses.util.io.FileCopyUtils;
import org.totschnig.myexpenses.util.io.StreamReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
  private static final CustomPropertyKey IS_BACKUP_FOLDER =
      new CustomPropertyKey("isBackupFolder", CustomPropertyKey.PRIVATE);
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

  @Override
  protected String readEncryptionToken() throws IOException {
    requireBaseFolder();
    try {
      return new StreamReader(getInputStream(baseFolder, ENCRYPTION_TOKEN_FILE_NAME)).read();
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public Exceptional<Void> setUp(String authToken, String encryptionPassword) {
    long lastFailedSync = sharedPreferences.getLong(KEY_LAST_FAILED_SYNC, 0);
    long currentBackOff = sharedPreferences.getLong(KEY_SYNC_BACKOFF, 0);
    long now = System.currentTimeMillis();
    if (lastFailedSync == 0 || lastFailedSync + currentBackOff <= now) {
      if (!googleApiClient.isConnected()) {
        ConnectionResult connectionResult = googleApiClient.blockingConnect();
        if (!connectionResult.isSuccess()) {
          final PendingIntent resolution = connectionResult.getResolution();
          if (resolution != null) {
            return Exceptional.of(new ResolvableSetupException(resolution, connectionResult.getErrorMessage()));
          }
          if (GoogleApiAvailability.getInstance().isUserResolvableError(connectionResult.getErrorCode())) {
            GoogleApiAvailability.getInstance().showErrorNotification(getContext(), connectionResult);
            //we return ResolvableSetupException to indicate that there is a resolution, but with null
            //resolution since GoogleApiAvailability is taking care of generating notification
            return Exceptional.of(new ResolvableSetupException(null, null));
          } else {
            return Exceptional.of(new IOException(getContext().getString(R.string.sync_io_error_cannot_connect)));
          }
        }
      }
      setUpInternal(now);
    } else {
      log().i("Not syncing, waiting for another %d milliseconds",
          lastFailedSync + currentBackOff - now);
    }
    return super.setUp(authToken, encryptionPassword);
  }

  @SuppressLint("ApplySharedPref")
  private void setUpInternal(long now) {
    Status status = Drive.DriveApi.requestSync(googleApiClient).await();
    if (!status.isSuccess()) {
      final int statusCode = status.getStatusCode();
      if (statusCode != DriveStatusCodes.DRIVE_RATE_LIMIT_EXCEEDED) {
        CrashHandler.reportWithTag(String.format(Locale.ROOT, "Sync failed with code %d", statusCode), SyncAdapter.TAG);
      } else {
        log().i("DRIVE_RATE_LIMIT_EXCEEDED");
      }
      long newBackOff = Math.min(sharedPreferences.getLong(KEY_SYNC_BACKOFF, 5000) * 2, 3600000);
      sharedPreferences.edit().putLong(KEY_LAST_FAILED_SYNC, now).putLong(KEY_SYNC_BACKOFF, newBackOff).commit();
    } else {
      sharedPreferences.edit().remove(KEY_LAST_FAILED_SYNC).remove(KEY_SYNC_BACKOFF).apply();
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
    final DriveFolder backupFolder = getBackupFolder(false);
    if (backupFolder != null) {
      return getInputStream(backupFolder, backupFile);
    } else {
      throw new IOException("No backup folder found");
    }
  }

  private InputStream getInputStream(DriveFolder folder, String title) throws IOException {
    Query query = new Query.Builder()
        .addFilter(Filters.eq(SearchableField.TITLE, title))
        .build();
    DriveApi.MetadataBufferResult metadataBufferResult =
        folder.queryChildren(googleApiClient, query).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Unable to query for file " + title);
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    if (metadataBuffer.getCount() != 1) {
      metadataBuffer.release();
      throw new FileNotFoundException("Unable to find file " + title);
    }
    final Metadata metadata = metadataBuffer.get(0);
    if (metadata.isFolder()) {
      throw new IOException(title + " is not a file but a folder");
    }
    DriveApi.DriveContentsResult driveContentsResult = metadata.getDriveId()
        .asDriveFile().open(googleApiClient, DriveFile.MODE_READ_ONLY, null).await();
    if (!driveContentsResult.getStatus().isSuccess()) {
      metadataBuffer.release();
      throw new IOException("Unable to open file " + title);
    }
    return driveContentsResult.getDriveContents().getInputStream();
  }

  @Override
  protected void saveUriToAccountDir(String fileName, Uri uri) throws IOException {
    saveUriToFolder(fileName, uri, accountFolder, true);
  }

  private void saveUriToFolder(String fileName, Uri uri, DriveFolder driveFolder, boolean maybeEncrypt) throws IOException {
    InputStream in = MyApplication.getInstance().getContentResolver().openInputStream(uri);
    if (in == null) {
      throw new IOException("Could not read " + uri.toString());
    }
    saveInputStream(fileName, maybeEncrypt ? maybeEncrypt(in) : in, getMimeType(fileName), driveFolder);
    in.close();
  }

  @Override
  public void storeBackup(Uri uri, String fileName) throws IOException {
    saveUriToFolder(fileName, uri, getBackupFolder(true), false);
  }

  @NonNull
  @Override
  public List<String> getStoredBackups(android.accounts.Account account) throws IOException {
    List<String> result = new ArrayList<>();
    final DriveFolder backupFolder = getBackupFolder(false);
    if (backupFolder != null) {
      DriveApi.MetadataBufferResult metadataBufferResult = backupFolder.listChildren(googleApiClient).await();
      if (!metadataBufferResult.getStatus().isSuccess()) {
        throw new IOException("Error while trying to get backup list");
      }
      MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
      result = Stream.of(metadataBuffer)
          .map(Metadata::getTitle)
          .toList();
      metadataBuffer.release();
    }
    return result;
  }

  @Override
  protected SequenceNumber getLastSequence(SequenceNumber start) throws IOException {
    final Comparator<Metadata> resourceComparator = (o1, o2) -> Utils.compare(getSequenceFromFileName(o1.getTitle()), getSequenceFromFileName(o2.getTitle()));

    DriveApi.MetadataBufferResult mainResult = accountFolder.listChildren(googleApiClient).await();
    if (!mainResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to get last sequence");
    }
    final MetadataBuffer metadataBuffer = mainResult.getMetadataBuffer();
    Optional<Metadata> lastShardOptional =
        Stream.of(metadataBuffer)
            .filter(metadata -> metadata.isFolder() && isAtLeastShardDir(start.shard, metadata.getTitle()))
            .max(resourceComparator);

    MetadataBuffer lastShard;
    int lastShardInt, reference;
    if (lastShardOptional.isPresent()) {
      lastShard = lastShardOptional.get().getDriveId().asDriveFolder().listChildren(googleApiClient).await().getMetadataBuffer();
      lastShardInt = getSequenceFromFileName(lastShardOptional.get().getTitle());
      reference = lastShardInt == start.shard ? start.number : 0;
    } else {
      if (start.shard > 0) return start;
      //mainResult can not be reused
      lastShard = accountFolder.listChildren(googleApiClient).await().getMetadataBuffer();
      lastShardInt = 0;
      reference = start.number;
    }
    metadataBuffer.release();
    SequenceNumber result = Stream.of(lastShard)
        .filter(metadata -> isNewerJsonFile(reference, metadata.getTitle()))
        .max(resourceComparator)
        .map(metadata -> new SequenceNumber(lastShardInt, getSequenceFromFileName(metadata.getTitle())))
        .orElse(start);
    lastShard.release();
    return result;
  }

  @Override
  void saveFileContentsToBase(String fileName, String fileContents, String mimeType, boolean maybeEncrypt) throws IOException {
    saveFileContents(baseFolder, fileName, fileContents, mimeType, maybeEncrypt);
  }

  @Override
  void saveFileContentsToAccountDir(String folder, String fileName, String fileContents, String mimeType, boolean maybeEncrypt) throws IOException {
    DriveFolder driveFolder;
    if (folder == null) {
      driveFolder = accountFolder;
    } else {
      final Optional<DriveFolder> driveFolderOptional = getShardFolder(folder);
      if (driveFolderOptional.isPresent()) {
        driveFolder = driveFolderOptional.get();
      } else {
        MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle(folder).build();
        DriveFolder.DriveFolderResult driveFolderResult = accountFolder.createFolder(googleApiClient, changeSet).await();
        if (!driveFolderResult.getStatus().isSuccess()) {
          throw new IOException("Unable to create folder");
        }
        driveFolder = driveFolderResult.getDriveFolder();
      }
    }
    saveFileContents(driveFolder, fileName, fileContents, mimeType, maybeEncrypt);
  }

  private void saveFileContents(DriveFolder driveFolder, String fileName, String fileContents, String mimeType, boolean maybeEncrypt) throws IOException {
    InputStream contents = toInputStream(fileContents, maybeEncrypt);
    saveInputStream(fileName, contents, mimeType, driveFolder);
    contents.close();
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
    log().i(driveFileResult.getStatus().toString());
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
  @NonNull
  public Optional<ChangeSet> getChangeSetSince(SequenceNumber sequenceNumber, Context context) throws IOException {
    DriveFolder shardFolder;
    if (sequenceNumber.shard == 0) {
      shardFolder = accountFolder;
    } else {
      shardFolder = getShardFolder("_" + sequenceNumber.shard).orElseThrow(() -> new IOException("shard folder not found"));
    }
    DriveApi.MetadataBufferResult metadataBufferResult = shardFolder.listChildren(googleApiClient).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Error while trying to get change set");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    log().i("Getting data from shard %d", sequenceNumber.shard);
    List<ChangeSet> changeSetList = new ArrayList<>();
    try {
      for (Metadata metadata: metadataBuffer) {
        if (isNewerJsonFile(sequenceNumber.number, metadata.getTitle())) {
          changeSetList.add(getChangeSetFromMetadata(sequenceNumber.shard, metadata));
        }
      }
    } finally {
      metadataBuffer.release();
    }
    int nextShard = sequenceNumber.shard + 1;
    while (true) {
      Optional<DriveFolder> nextShardFolder = getShardFolder("_" + nextShard);
      if (nextShardFolder.isPresent()) {
        metadataBufferResult = nextShardFolder.get().listChildren(googleApiClient).await();
        if (!metadataBufferResult.getStatus().isSuccess()) {
          throw new IOException("Error while trying to get change set");
        }
        metadataBuffer = metadataBufferResult.getMetadataBuffer();
        log().i("Getting data from shard %d", nextShard);
        try {
          for (Metadata metadata: metadataBuffer) {
            if (isNewerJsonFile(0, metadata.getTitle())) {
              changeSetList.add(getChangeSetFromMetadata(nextShard, metadata));
            }
          }
        } finally {
          metadataBuffer.release();
        }
        nextShard++;
      } else {
        break;
      }
    }
    return merge(changeSetList);
  }

  private Optional<DriveFolder> getShardFolder(String shard) throws IOException {
    DriveApi.MetadataBufferResult metadataBufferResult = accountFolder.listChildren(googleApiClient).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Unable to list children of account folder");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    Optional<DriveFolder> result = Stream.of(metadataBuffer)
        .filter(metadata -> metadata.getTitle().equals(shard))
        .findFirst()
        .map(metadata -> metadata.getDriveId().asDriveFolder());
    metadataBuffer.release();
    return result;
  }

  private ChangeSet getChangeSetFromMetadata(int shard, Metadata metadata) throws IOException {
    DriveId driveId = metadata.getDriveId();
    DriveApi.DriveContentsResult driveContentsResult =
        driveId.asDriveFile().open(googleApiClient, DriveFile.MODE_READ_ONLY, null).await();
    final String title = metadata.getTitle();
    if (!driveContentsResult.getStatus().isSuccess()) {
      throw new IOException(String.format("Unable to open %s", title));
    }
    log().i("Getting data from file %s", title);
    DriveContents driveContents = driveContentsResult.getDriveContents();
    try {
      return getChangeSetFromInputStream(new SequenceNumber(shard, getSequenceFromFileName(title)),
          driveContents.getInputStream());
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
    Stream<AccountMetaData> result;
    requireBaseFolder();
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
    return result;
  }

  private Optional<AccountMetaData> getAccountMetaDataFromDriveMetadata(Metadata metadata) {
    if (!metadata.isFolder()) {
      return Optional.empty();
    }
    Query query = new Query.Builder()
        .addFilter(Filters.eq(SearchableField.TITLE, getAccountMetadataFilename()))
        .build();
    DriveApi.MetadataBufferResult metadataBufferResult =
        metadata.getDriveId().asDriveFolder().queryChildren(googleApiClient, query).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      CrashHandler.report("Unable to query for metadata file");
      return Optional.empty();
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    if (metadataBuffer.getCount() == 1) {
      DriveApi.DriveContentsResult driveContentsResult = metadataBuffer.get(0).getDriveId()
          .asDriveFile().open(googleApiClient, DriveFile.MODE_READ_ONLY, null).await();
      if (!driveContentsResult.getStatus().isSuccess()) {
        CrashHandler.report("Unable to open metadata file");
        metadataBuffer.release();
        return Optional.empty();
      }
      final InputStream inputStream = driveContentsResult.getDriveContents().getInputStream();
      final Optional<AccountMetaData> result = getAccountMetaDataFromInputStream(inputStream);
      metadataBuffer.release();
      return result;
    }

    //legacy
    Map<CustomPropertyKey, String> customProperties = metadata.getCustomProperties();
    if (metadata.isTrashed()) {
      Timber.d("Folder is trashed");
      return Optional.empty();
    }
    String uuid = customProperties.get(ACCOUNT_METADATA_UUID_KEY);
    if (uuid == null) {
      Timber.d("UUID property not set");
      return Optional.empty();
    }
    //TODO add default values
    return Optional.of(AccountMetaData.builder()
        .setType(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_TYPE_KEY, AccountType.CASH.name()))
        .setOpeningBalance(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_OPENING_BALANCE_KEY, 0L))
        .setDescription(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_DESCRIPTION_KEY, ""))
        .setColor(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_COLOR_KEY, Account.DEFAULT_COLOR))
        .setCurrency(getPropertyWithDefault(customProperties, ACCOUNT_METADATA_CURRENCY_KEY,
            Utils.getHomeCurrency().code()))
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

  private boolean getPropertyWithDefault(Map<CustomPropertyKey, String> customProperties,
                                     CustomPropertyKey key,
                                     boolean defaultValue) {
    String result = customProperties.get(key);
    return result != null ? Boolean.valueOf(result) : defaultValue;
  }

  @Nullable
  private DriveFolder getBackupFolder(boolean require) throws IOException {
    requireBaseFolder();
    Query query = new Query.Builder()
        .addFilter(Filters.eq(SearchableField.TITLE, BACKUP_FOLDER_NAME))
        .build();
    DriveApi.MetadataBufferResult metadataBufferResult =
        baseFolder.queryChildren(googleApiClient, query).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Unable to query for backup folder");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    if (metadataBuffer.getCount() != 0) {
      for (Metadata metadata: metadataBuffer) {
        if (getPropertyWithDefault(metadata.getCustomProperties(), IS_BACKUP_FOLDER, false)) {
          DriveFolder result = metadata.getDriveId().asDriveFolder();
          metadataBuffer.release();
          return result;
        }
      }

    }
    metadataBuffer.release();
    if (require) {
      MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
          .setTitle(BACKUP_FOLDER_NAME)
          .setCustomProperty(IS_BACKUP_FOLDER, "true")
          .build();
      DriveFolder.DriveFolderResult driveFolderResult = baseFolder.createFolder(googleApiClient, changeSet).await();
      if (!driveFolderResult.getStatus().isSuccess()) {
        throw new IOException("Unable to create backup folder");
      }
      return driveFolderResult.getDriveFolder();
    }
    return null;
  }

  private Optional<DriveFolder> getExistingAccountFolder(String uuid) throws IOException {
    requireBaseFolder();
    DriveApi.MetadataBufferResult metadataBufferResult = baseFolder.listChildren(googleApiClient).await();
    if (!metadataBufferResult.getStatus().isSuccess()) {
      throw new IOException("Unable to list children of base folder");
    }
    MetadataBuffer metadataBuffer = metadataBufferResult.getMetadataBuffer();
    Optional<DriveFolder> result = Stream.of(metadataBuffer)
        .filter(metadata -> (uuid.equals(metadata.getTitle()) ||
            //legacy:
            uuid.equals(metadata.getCustomProperties().get(ACCOUNT_METADATA_UUID_KEY))) &&
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
      MetadataChangeSet.Builder builder = new MetadataChangeSet.Builder().setTitle(account.uuid);
      MetadataChangeSet changeSet = builder.build();
      DriveFolder.DriveFolderResult driveFolderResult = baseFolder.createFolder(googleApiClient, changeSet).await();
      if (driveFolderResult.getStatus().isSuccess()) {
        accountFolder = driveFolderResult.getDriveFolder();
        createWarningFile();
        saveFileContentsToAccountDir(null, getAccountMetadataFilename(), buildMetadata(account), getMimetypeForData(), true);
      } else {
        throw new IOException("Error while creating account folder");
      }
    }
  }

  private void requireBaseFolder() throws IOException {
    if (baseFolder == null) {
      DriveApi.DriveIdResult driveIdResult = Drive.DriveApi.fetchDriveId(googleApiClient, folderId).await();
      if (driveIdResult.getStatus().isSuccess()) {
        baseFolder = driveIdResult.getDriveId().asDriveFolder();
      } else {
        throw new IOException("Base folder not available");
      }
    }
  }
}
