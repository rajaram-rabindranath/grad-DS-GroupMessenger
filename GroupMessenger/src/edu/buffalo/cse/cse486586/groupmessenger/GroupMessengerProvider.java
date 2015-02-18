package edu.buffalo.cse.cse486586.groupmessenger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;


/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author steveko
 *
 */
/*=========================================================================
 * Class name   : GroupMessengerProvider
 * Description  : Sets up a database in sqlite and provides functions to
 * 					insert content and query information from the database
 * Author's		: steveko(base code)
 * 				  Rajaram Rabindranath(implementation)
 *=========================================================================*/
public class GroupMessengerProvider extends ContentProvider 
{
	static final String uri = "content://edu.buffalo.cse.cse486586.groupmessenger.provider";
	static final Uri grpMessengerURI = Uri.parse(uri);
	static final String TAG = "GrpMessenger";
	// database details
	static final String dbName 	="cse586_486_prj2_totCausal";
	static final String tableName	= "messages_totCausal";
	static int dbVersion = 1; // overkill -- for prj2
	private SQLiteDatabase sqliteDB = null;	
	static dataAccess db_conduit = null; 
	static Context grpMessengerContext = null;

	
	
	
	/*=========================================================================
	 * Class name   : dataAccess
	 * Description  : extends SQLiteOpenHelper Sets up a database in sqlite					
	 * Author's		: Rajaram Rabindranath(implementation)
	 *=========================================================================*/
    private class dataAccess extends SQLiteOpenHelper 
    {
    		final String sqlStatement_CreateTable = "create table "+tableName+"( key text not null,"+" value text not null);";
    		final String TAG = dataAccess.class.getName();
    		
    		public dataAccess(Context context)
    		{
    			super(context,dbName, null,dbVersion);
    		}

    		/*
    		 *get called when getWriteable database is called
    		 */
    		public void onCreate(SQLiteDatabase sqliteDB) 
    		{
    			sqliteDB.execSQL(sqlStatement_CreateTable); // creates a table
    			Log.e(TAG,"CREATING TABLE");
    		}

    		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) 
    		{
    			Log.e(TAG,"I have been asked to upgrade .. don't know what to do");
    		}    	
    }
	
	/*=========================================================================
     * Function   : onCreate()
     * Description: Needs to setup a database
     * Parameters : void
     * Return	  : boolean [success/failure]
     *=========================================================================*/
    public boolean onCreate() 
    {
		grpMessengerContext = getContext();
		db_conduit = new dataAccess(grpMessengerContext);
		
		// permissions to be writable
		sqliteDB = db_conduit.getWritableDatabase();
		
		if(sqliteDB == null)
		{
			Log.e(TAG,"COULD NOT CREATE DATABASE!");
			return false;
		}
		return true;	
    }
    
    /*=========================================================================
     * Function   : insert
     * Description: inserts the <key,value>[<msg sequence,msg>] into the sqlite
     * 				database
     * Parameters : Uri uri, ContentValues values
     * Return	  : Uri 
     *=========================================================================*/
    public Uri insert(Uri uri, ContentValues values)
    {
    	long rowID = sqliteDB.insert(tableName, "", values);
        if(rowID < 0)
        {
        	Log.e(TAG+" insert","INSERT FAIL");
        	return null;
        }
        getContext().getContentResolver().notifyChange(uri, null);
        Log.v(TAG+" insert", values.toString()+" ---  "+rowID);
        return grpMessengerURI;
    }

    /*=========================================================================
     * Function   : query
     * Description: queries the database given a query "selection" and then
     * 				then returns the results in the Cursor object has
     * 				<msg sequence num,message>
     * Parameters : Uri uri, String[] projection, String selection, 
     * 				String[] selectionArgs,String sortOrder
     * Return	  : Cursor 
     *=========================================================================*/
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,String sortOrder) 
    {
    	SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
		queryBuilder.setTables(tableName);
        sqliteDB = db_conduit.getReadableDatabase();
        String queryArgs[]={selection};
        Cursor cursor = sqliteDB.rawQuery("select * from "+tableName+" where key=?",queryArgs);
        if(cursor == null) 
    	{
        	Log.e(TAG+" query","Query Failure -- cursor null");
        	return null;
    	}       
        
        // make sure that potential listeners are getting notified
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        Log.v(TAG+" query", selection);
        return cursor;
    }

	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
		// TODO Auto-generated method stub
		return 0;
	}
}


