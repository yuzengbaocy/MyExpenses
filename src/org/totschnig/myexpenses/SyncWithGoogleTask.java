package org.totschnig.myexpenses;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import android.accounts.AccountManager;
import android.os.AsyncTask;
import android.widget.Toast;

import foo.joeledstrom.spreadsheets.Spreadsheet;
import foo.joeledstrom.spreadsheets.SpreadsheetsService;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.FeedIterator;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.TokenSupplier;
import foo.joeledstrom.spreadsheets.Worksheet;

public class SyncWithGoogleTask extends AsyncTask<Void, Void, Void> {
    AccountManager accountManager;
    MyExpenses context;
    
    public SyncWithGoogleTask(MyExpenses context) {
      this.context = context;
    }
    public Void doInBackground(Void... params) {
        TokenSupplier supplier = new TokenSupplier() {
            @Override
            public void invalidateToken(String token) {
                AccountManager.get(context).invalidateAuthToken("com.google", token);
            }
            @Override
            public String getToken(String authTokenType) {
              if (authTokenType.equals("writely")) {
                return context.writelyAuthToken;
              } else if (authTokenType.equals("wise")) {
                return context.wiseAuthToken;
              }
              return null; 
            }
        };


        SpreadsheetsService service = new SpreadsheetsService("totschnig-myexpenses-v1.5.0", supplier);

        try {
            final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            service.createSpreadsheet("My expenses", true);
            FeedIterator<Spreadsheet> spreadsheetFeed = service.getSpreadsheets();
            // get all spreadsheets
            
            List<Spreadsheet> spreadsheets = spreadsheetFeed.getEntries(); // reads and parses the whole stream
            Spreadsheet firstSpreadsheet = spreadsheets.get(0);

            
            Worksheet sheet = firstSpreadsheet.addWorksheet("Expenses", Arrays.asList(new String[] {"date", "amount", "comment","label","payee"}));
            context.mExpensesCursor.moveToFirst();
            while( context.mExpensesCursor.getPosition() < context.mExpensesCursor.getCount() ) {
              String comment = context.mExpensesCursor.getString(
                  context.mExpensesCursor.getColumnIndexOrThrow(ExpensesDbAdapter.KEY_COMMENT));
              final String fComment = (comment == null || comment.length() == 0) ? "" : comment;
              String label =  context.mExpensesCursor.getString(
                  context.mExpensesCursor.getColumnIndexOrThrow("label"));

              final String fLabel;
              if (label == null || label.length() == 0) {
                fLabel =  "";
              } else {
                long transfer_peer = context.mExpensesCursor.getLong(
                    context.mExpensesCursor.getColumnIndexOrThrow(ExpensesDbAdapter.KEY_TRANSFER_PEER));
                if (transfer_peer != 0) {
                  label = "[" + label + "]";
                }
                fLabel = label;
              }

              String payee = context.mExpensesCursor.getString(
                  context.mExpensesCursor.getColumnIndexOrThrow("payee"));
              final String fPayee = (payee == null || payee.length() == 0) ? "" : payee;
              sheet.addRow(new HashMap<String, String>() {{
                put("date", formatter.format(Timestamp.valueOf(context.mExpensesCursor.getString(
                    context.mExpensesCursor.getColumnIndexOrThrow(ExpensesDbAdapter.KEY_DATE)))));
                put("amount", context.mExpensesCursor.getString(
                    context.mExpensesCursor.getColumnIndexOrThrow(ExpensesDbAdapter.KEY_AMOUNT)));
                put("comment",fComment);
                put("label",fLabel);
                put("payee",fPayee);
            }});
              context.mExpensesCursor.moveToNext();
            }
            context.mExpensesCursor.moveToFirst();           
            // commitChanges() returns false, if it couldnt safely change the row
            // (someone else changed the row before we commited)
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return null;
    }
    protected void onPostExecute(Void v) {
      Toast.makeText(context, "spreadsheet successfully created", Toast.LENGTH_LONG).show();
    }
}