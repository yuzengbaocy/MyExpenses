package org.totschnig.myexpenses

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DbTransactionTest {

    @Test
    fun catchErrorAndCompleteTransaction() {
        val mDb = MyDbHelper(RuntimeEnvironment.application).getWritableDatabase()
        mDb.beginTransaction()
        mDb.execSQL("INSERT INTO $TABLE VALUES ('test')")
        try {
            mDb.execSQL("INSERT INTO hudriwusch VALUES ('test')")
        } catch (e: SQLException) {
        }
        mDb.setTransactionSuccessful()
        val cursor = mDb.query(TABLE, null, null, null, null, null, null)
        assertThat(cursor.count).isEqualTo(1)
        cursor.moveToFirst()
        assertThat(cursor.getString(0)).isEqualTo("test")
        cursor.close()
        mDb.close()
    }
    private class MyDbHelper internal constructor(context: Context?) : SQLiteOpenHelper(context, "dbtransactiontest", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $TABLE ($COLUMN STRING not null)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    }
    companion object {
        const val TABLE = "tabelle"
        const val COLUMN ="spalte"
    }
}