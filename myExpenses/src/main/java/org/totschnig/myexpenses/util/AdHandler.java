package org.totschnig.myexpenses.util;

public interface AdHandler {
  void init();

  void onEditTransactionResult();

  void onResume();

  void onDestroy();

  void onPause();

}
