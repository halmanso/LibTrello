package com.openatk.libtrello;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

//Singleton
//Handles syncing of all boards/lists/cards
public class TrelloController {
	private String OrganizationID = "";
	private String TrelloKey = "";
	private String TrelloToken = "";
	private Context AppContext;
	
	private Boolean currentlySyncing = false;
	
	private Handler autoSync_handler = new Handler();;
	Runnable autoSync_task;
	
	private ISyncController syncController = null;
	
	public static final String PREFS_NAME = "TrelloClient";
	
	private static SimpleDateFormat dateFormater;
	private static SimpleDateFormat dateFormaterReport;
	
	private Boolean syncAllBoards = false;
	private Boolean autoSyncing = false;
	private Boolean reAddMembersToBoards = false;
	List <String> membersAdded = new ArrayList<String>();; 
	List <String> membersDeleted = new ArrayList<String>();; 


	List <TrelloBoard> trelloBoards;
	List <TrelloList> trelloLists;
	List <TrelloCard> trelloCards; 
	
	//TODO put these in library
	private static final Integer AUTO_SYNC_INTERVAL = 30;

	private static final String COL_ID = "_id";
	private static final String COL_NAME = "name";
	private static final String COL_PACKAGE_NAME = "package_name";
	private static final String COL_ALLOW_SYNCING = "allow_syncing";
	private static final String COL_LAST_SYNC = "lastsync";
    public static final String COL_AUTO_SYNC = "auto_sync";
    public static final String COL_BOARD_NAME = "board_name";
	private static final String AUTHORITY = "com.openatk.trello.provider";
	private static final String BASE_PATH = "apps";
	private static final String LOGINS_PATH = "logins";
	private static final String MEMBERS_PATH = "organization_members";

	private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);
	private static final Uri CONTENT_URI_LOGINS = Uri.parse("content://" + AUTHORITY + "/" + LOGINS_PATH);
	public static final Uri CONTENT_URI_ORGANIZATION_MEMBERS = Uri.parse("content://" + AUTHORITY + "/" + MEMBERS_PATH);

	
	public TrelloController(Context AppContext) {
		super();
		this.AppContext = AppContext;
		
		//2013-03-29T11:22:30.368Z
		dateFormater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
		dateFormater.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		dateFormaterReport = new SimpleDateFormat("MMM dd h:mm a", Locale.US);
		dateFormaterReport.setTimeZone(TimeZone.getDefault());
	}
	
	public void setSyncController(ISyncController syncController){
		this.syncController = syncController;
		
		stopAutoSync();
		startAutoSync();
	}

	
	public void startAutoSync(){
		if(autoSyncing == false){
			if(autoSyncEnabled()){
				beginAutoSync(AUTO_SYNC_INTERVAL);
			}
		}
	}
	
	private void beginAutoSync(final Integer intervalSeconds){
		final TrelloController parent = this;
		autoSync_task = new Runnable(){
		    @Override 
		    public void run() {
		    	parent.sync();
		        autoSync_handler.postDelayed(this, (intervalSeconds * 1000));
		    }
		};
		autoSync_task.run();
	}
	
	public void stopAutoSync(){
		autoSync_handler.removeCallbacksAndMessages(null);
		autoSyncing = false;
		Log.d("TrelloController - stopAutoSync", "autoSync stopped");
	}	

	public boolean autoSyncEnabled(){
		String[] projection = { COL_ID, COL_PACKAGE_NAME, COL_AUTO_SYNC };
		Cursor mCursor = AppContext.getContentResolver().query(CONTENT_URI, projection, null, null, null);
		Boolean found = false;
		if (null == mCursor) {
			// If the Cursor is empty, the provider found no matches
			Log.d("TrelloController - autoSyncEnabled", "Null cursor");
		} else if (mCursor.getCount() < 1) {
			Log.d("TrelloController - autoSyncEnabled", "None found");
		} else {
			//Results
			String packageName = AppContext.getPackageName();
			while(mCursor.moveToNext()){
				String matchPackageName = mCursor.getString(mCursor.getColumnIndex(COL_PACKAGE_NAME));
				int enabled = mCursor.getInt(mCursor.getColumnIndex(COL_AUTO_SYNC));
				//Log.d("TrelloController - syncingEnabled", "Match: " + packageName + " with " + matchPackageName);
				if(packageName.contentEquals(matchPackageName)){
					if(enabled == 1){
						found = true;
					}
				}
			}
		}
		if(mCursor != null) mCursor.close();
		return found;
	}
	
	public boolean syncingEnabled(){
		String[] projection = { COL_ID, COL_PACKAGE_NAME, COL_ALLOW_SYNCING };
		Cursor mCursor = AppContext.getContentResolver().query(CONTENT_URI, projection, null, null, null);
		Boolean found = false;
		if (null == mCursor) {
			// If the Cursor is empty, the provider found no matches
			Log.d("TrelloController - syncingEnabled", "Null cursor");
		} else if (mCursor.getCount() < 1) {
			Log.d("TrelloController - syncingEnabled", "None found");
		} else {
			//Results
			String packageName = AppContext.getPackageName();
			while(mCursor.moveToNext()){
				String matchPackageName = mCursor.getString(mCursor.getColumnIndex(COL_PACKAGE_NAME));
				int enabled = mCursor.getInt(mCursor.getColumnIndex(COL_ALLOW_SYNCING));
				//Log.d("TrelloController - syncingEnabled", "Match: " + packageName + " with " + matchPackageName);
				if(packageName.contentEquals(matchPackageName)){
					if(enabled == 1){
						found = true;
					}
				}
			}
		}
		if(mCursor != null) mCursor.close();
		return found;
	}
	
	private void reportSyncTime(String lastSync){
		Log.d("TrelloController - reportSyncTime", "Reporting:" + lastSync);
		ContentValues values = new ContentValues();
		values.put(COL_LAST_SYNC, lastSync);
		String packageName = AppContext.getPackageName();
		String where = COL_PACKAGE_NAME + " = '" +  packageName + "'";
		int updated = AppContext.getContentResolver().update(CONTENT_URI, values, where, null);
		Log.d("TrelloController - reportSyncTime", "Updated " + Integer.toString(updated) + " rows");
	}
	
	private void reportBoardName(String boardName){
		//Send board name to trello app so you can view the board in trello in the browser
		ContentValues values = new ContentValues();
		values.put(COL_BOARD_NAME, boardName);
		String packageName = AppContext.getPackageName();
		String where = COL_PACKAGE_NAME + " = '" +  packageName + "'";
		AppContext.getContentResolver().update(CONTENT_URI, values, where, null);
	}
	
	public boolean getAPIKeys(){
		String COL_SECRET = "secret";
		String COL_TOKEN = "token";
		String COL_APIKEY = "apikey";
	    String COL_ORGO_ID = "orgo_id";
		
		String[] projection = { COL_SECRET, COL_TOKEN, COL_APIKEY, COL_ORGO_ID };
		Cursor mCursor = AppContext.getContentResolver().query(CONTENT_URI_LOGINS, projection, null, null, null);
		Boolean found = false;
		if (null == mCursor) {
			// If the Cursor is empty, the provider found no matches
			Log.d("TrelloController - getAPIKeys", "Null cursor");
		} else if (mCursor.getCount() < 1) {
			Log.d("TrelloController - getAPIKeys", "None found");
		} else {
			//Results
			if(mCursor.moveToFirst()){
				SharedPreferences settings = AppContext.getSharedPreferences(PREFS_NAME, 0);
				String oldTrelloKey = settings.getString("TrelloKey", "");
				String oldTrelloToken = settings.getString("TrelloToken", "");
				String oldOrganizationID = settings.getString("OrganizationID", "");
				Log.d("OldOrgoID:",oldOrganizationID);
				TrelloKey = mCursor.getString(mCursor.getColumnIndex(COL_APIKEY)).trim();
				TrelloToken = mCursor.getString(mCursor.getColumnIndex(COL_TOKEN)).trim();
				OrganizationID = mCursor.getString(mCursor.getColumnIndex(COL_ORGO_ID)).trim();
				found = true;
				Log.d("CurrentOrgoID:", OrganizationID);
				if(oldOrganizationID.contentEquals(OrganizationID) == false){
					if(oldOrganizationID.length() > 0){
						Log.d("syncAllBoards", "syncAllBoards");
						syncAllBoards = true; //New organization
						syncController.changeOrganization();
					}
					//Save new olds
					 SharedPreferences.Editor editor = settings.edit();
					 editor.putString("TrelloKey", TrelloKey);
					 editor.putString("TrelloToken", TrelloToken);
					 editor.putString("OrganizationID", OrganizationID);
					 editor.commit();
				}
			}
			Log.d("TrelloController - getAPIKeys", "Key:" + TrelloKey.trim());
			Log.d("TrelloController - getAPIKeys", "Token:" + TrelloToken.trim());
			Log.d("TrelloController - getAPIKeys", "Organization ID:" + OrganizationID.trim());
		}
		mCursor.close();
		return found;
	}
	
	private boolean getOrganizationMembers(){
		Boolean membersChanged = false;
	    String COL_MEMBER_ID = "member_id";
	    String COL_ORGO_ID = "orgo_id";
	    String where = COL_ORGO_ID + " = '" + OrganizationID + "'";
	    	    
		String[] projection = { COL_MEMBER_ID, COL_ORGO_ID };
		try {
			AppContext.getContentResolver().query(CONTENT_URI_ORGANIZATION_MEMBERS, projection, null, null, null);
		} catch(Exception e){
			//Old version v1 need to update trello
			Log.d("Error", "Invaild URI Trello needs updated");
			return false;
		}
		
		Cursor mCursor = AppContext.getContentResolver().query(CONTENT_URI_ORGANIZATION_MEMBERS, projection, where, null, null);
		if (null == mCursor) {
			// If the Cursor is empty, the provider found no matches
			Log.d("TrelloController - getOrganizationMembers", "Null cursor");
		} else if (mCursor.getCount() < 1) {
			Log.d("TrelloController - getOrganizationMembers", "None found");
		} else {
			//Results
			List<String> memberIds = new ArrayList<String>();
			
			SharedPreferences settings = AppContext.getSharedPreferences(PREFS_NAME, 0);
			Set<String> set = settings.getStringSet("organizationMembers", null);
			if(set == null){
				Log.d("TrelloController - getOrganizationMembers", "First Time");
				set = new HashSet<String>();
			}
			
			while(mCursor.moveToNext()){
				String memberId = mCursor.getString(mCursor.getColumnIndex(COL_MEMBER_ID)).trim();
				memberIds.add(memberId);
				Log.d("TrelloController - getOrganizationMembers", "Cursor found id:" + memberId);
				if(set.contains(memberId) == false){
					//Member added
					set.add(memberId);
					membersAdded.add(memberId);
					membersChanged = true;
					Log.d("TrelloController - getOrganizationMembers", "Set doesnt have this");
				}
			}
			mCursor.close();
			
			String[] setArray = (String[]) set.toArray(new String[set.size()]);
			for(int i=0; i< setArray.length; i++){
				if(memberIds.contains(setArray[i]) == false){
					set.remove(setArray[i]);
					membersDeleted.add(setArray[i]);
					membersChanged = true;
					Log.d("TrelloController - getOrganizationMembers", "A member was removed");
				}
			}
		
			if(membersChanged){
				SharedPreferences.Editor editor = settings.edit();
				editor.putStringSet("organizationMembers", set);
				editor.commit();
			}
			
			Log.d("TrelloController - getOrganizationMembers", "Organization Members Changed:" + Boolean.toString(membersChanged));
		}
		mCursor.close();

		return membersChanged;
	}
	
	public void sync(){
		if(this.syncingEnabled()){
			if(this.getAPIKeys()){
				if(isNetworkAvailable()){
					if(this.getOrganizationMembers()){
						reAddMembersToBoards = true;
					}
					//Sync
					if(currentlySyncing){
						Log.d("TrelloController - sync", "Already syncing");
					} else {
						if(isNetworkConnected(AppContext)){
							currentlySyncing = true;
							new SyncTask(this, syncController).execute();
						} else {
							Log.d("TrelloController - sync", "No network connection");
						}
					}
				} else {
					Log.d("TrelloController - sync", "No network connected");
				}
			} else {
				Log.d("TrelloController - sync", "API Keys not loaded");
			}
		} else {
			Log.d("TrelloController - sync", "Syncing not enabled");
		}
	}
	
	private boolean isNetworkConnected(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		if (ni == null) {
			// There are no active networks.
			return false;
		}
	    return true;
	} 
	
	private class SyncTask extends AsyncTask<Void, Integer, Void> {
		private ISyncController syncController;
		private TrelloController parent;
		public SyncTask(TrelloController parent, ISyncController syncController){
			this.syncController = syncController;
			this.parent = parent;
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			// TODO Auto-generated method stub
			this.parent.syncThread(this.syncController);
			return null;
		}
		
		@Override
	    protected void onProgressUpdate(Integer... progress) {
	         //setProgressPercent(progress[0]);
	    }
		@Override
	    protected void onPostExecute(Void result) {
			parent.currentlySyncing = false;
			syncController.finishedSync();
	    }
	 }
	
	private Boolean syncThread(ISyncController syncController){
		//Check if syncing is enabled
		
		trelloLists = new ArrayList<TrelloList>();
		trelloCards = new ArrayList<TrelloCard>();	

		List<ICard> localCards;
		List<IList> localLists;
		List<IBoard> localBoards;
		Boolean addedBoards = false;
		Boolean internetFailed = false;
		
		localBoards = syncController.getLocalBoards();
		if(reAddMembersToBoards){
			if(localBoards.isEmpty() == false){
				for(int i=0; i < localBoards.size(); i++){
					if(localBoards.get(i).getTrelloId().length() != 0){
						//Add and remove all members to this board
						Iterator<String> iterator = membersAdded.iterator();
						while (iterator.hasNext()) {
							String member = iterator.next();
							if(addMemberToBoard(member, localBoards.get(i).getTrelloId()) == true){
								iterator.remove();
							}
						}
						iterator = membersDeleted.iterator();
						while (iterator.hasNext()) {
							String member = iterator.next();
							if(removeMemberFromBoard(member, localBoards.get(i).getTrelloId()) == true){
								iterator.remove();
							}
						}
					}
				}
			}
			reAddMembersToBoards = false;
		}
		
		//TODO only do if changes
		if(localBoards.isEmpty() == false){
			if(localBoards.get(0).getTrelloId().length() != 0){
				reportBoardName(localBoards.get(0).getTrelloId());
			}
			//Check if any without an id
			for(int i=0; i < localBoards.size(); i++){
				if(localBoards.get(i).getTrelloId().length() == 0){
					//Need to sync boards
					syncAllBoards = true;
				}
			}
		}
		
		if(syncAllBoards == true){
			Log.d("SyncingAll Boards", "SyncingAll Boards");
			//Download all boards from Trello
			trelloBoards = getBoards();
			if(trelloBoards == null){
				internetFailed = true;
			} else {
				//Loop through boards and update accordingly
				for(int i=0; i < trelloBoards.size(); i++){
					TrelloBoard board = trelloBoards.get(i);
					
					//Find in LocalBoards
					IBoard localBoard = null;
					for(int j=0; j < localBoards.size(); j++){
						if(board.getTrelloId().contentEquals(localBoards.get(j).getTrelloId())){
							localBoard = localBoards.get(j);
						}
					}
					if(localBoard != null){
						if(localBoard.hasLocalChanges()){
							//Overwrite trello
							syncController.setBoardLocalChanges(localBoard, false);
							Boolean result = UpdateBoardOnTrello(localBoard);
							if(result == false){
								//If failed to overwrite
								syncController.setBoardLocalChanges(localBoard, true);
							}
						} else {
							//No changes on local
							//Try to convert and check and overwrite local if different
							syncController.updateBoard(localBoard, board);
						}
					} else {
						//New board on trello
						//Try to convert add to local if converts success
						syncController.addBoard(board);
					}
				}
				//Get again, some of trello's may have replaced our local copies without id's
				localBoards = syncController.getLocalBoards();
				//Add boards from local to trello that aren't on trello yet
				for(int j=0; j < localBoards.size(); j++){
					IBoard localBoard = localBoards.get(j);
					if(localBoard.getTrelloId().length() == 0){
						//Add board to trello
						addedBoards = true;
						String newId = AddBoardToTrello(localBoard);
						syncController.setBoardLocalChanges(localBoard, false);
						if(newId != null){
							Log.d("TrelloController - sync", "Updating board id");
							syncController.setBoardTrelloId(localBoard, newId);
						} else {
							syncController.setBoardLocalChanges(localBoard, true);
						}
					}
				}
				syncAllBoards = false;
			}
		}
		
		if(internetFailed == true) return false;
		
		if(addedBoards){
			//Get again, trello id changed
			localBoards = syncController.getLocalBoards();
		}
		for(int h=0; h<localBoards.size(); h++){
			IBoard localBoard = localBoards.get(h);
			
			if(localBoard.getTrelloId().length() != 0){
				//Sync Cards and Lists of all localBoards with trelloIds
				TrelloBoard trelloBoard = getListsAndCards(localBoard.getTrelloId());
				
				if(trelloBoard == null) return false; //Internet failed
				
				//*** BOARD SYNC ***
				//Find in LocalBoards
				if(localBoard.hasLocalChanges()){
					//Overwrite trello
					syncController.setBoardLocalChanges(localBoard, false);
					Boolean result = UpdateBoardOnTrello(localBoard);
					if(result == false){
						//If failed to overwrite
						syncController.setBoardLocalChanges(localBoard, true);
					}
				} else {
					//No changes on local
					//Try to convert and check and overwrite local if different
					syncController.updateBoard(localBoard, trelloBoard);
				}
				
				//Check if local board is still wanted
				Boolean localBoardExists = false;
				List<IBoard> localBoardsAfterSync = syncController.getLocalBoards();
				for(int m=0; m<localBoardsAfterSync.size(); m++){
					if(localBoardsAfterSync.get(m).getTrelloId().contentEquals(localBoard.getTrelloId())){
						localBoardExists = true;
					}
				}
				
				if(localBoardExists){				
					//**** LISTS SYNC ****
					//Loop through lists and update accordingly
					localLists = syncController.getLocalLists();
					for(int i=0; i < trelloLists.size(); i++){
						TrelloList list = trelloLists.get(i);
						
						//Find in LocalLists
						IList localList = null;
						for(int j=0; j < localLists.size(); j++){
							if(list.getTrelloId().contentEquals(localLists.get(j).getTrelloId())){
								localList = localLists.get(j);
							}
						}
						if(localList != null){
							if(localList.hasLocalChanges()){
								//Overwrite trello
								syncController.setListLocalChanges(localList, false);
								Boolean result = UpdateListOnTrello(localList);
								if(result == false){
									//If failed to overwrite
									syncController.setListLocalChanges(localList, true);
								}
							} else {
								//No changes on local
								//Try to convert and check and overwrite local if different
								syncController.updateList(localList, list);
							}
						} else {
							//New list on trello
							//Try to convert add to local if converts success
							syncController.addList(list);
						}
					}
					
					//Add lists from local to trello that aren't on trello yet and are in this board
					//Get again, some of trello's may have replaced our local copies without id's
					localLists = syncController.getLocalLists();
					for(int j=0; j < localLists.size(); j++){
						IList localList = localLists.get(j);
						if(localList.getTrelloId().length() == 0 && localList.getBoardId().contentEquals(localBoard.getTrelloId())){
							//Add list to trello
							String newId = AddListToTrello(localList);
							syncController.setListLocalChanges(localList, false);
							if(newId != null){
								syncController.setListTrelloId(localList, newId);
							} else {
								syncController.setListLocalChanges(localList, true);
							}
						}
					}
					
					Log.d("TrelloController - sync", "TrelloCards length:" + Integer.toString(trelloCards.size()));
					//**** CARDS SYNC ****
					//Loop through cards and update accordingly
					localCards = syncController.getLocalCards();
					//Add cards that are on trello but not in local db
					//Update cards either local to trello or update local with 
					for(int i=0; i < trelloCards.size(); i++){
						TrelloCard card = trelloCards.get(i);
	
						//Find in localCards
						ICard localCard = null;
						for(int j=0; j < localCards.size(); j++){
							if(card.getTrelloId().contentEquals(localCards.get(j).getTrelloId())){
								localCard = localCards.get(j);
							}
						}
						if(localCard != null){
							if(localCard.hasLocalChanges()){
								//Overwrite trello if listId exists, if listId doesn't exist wait till next sync
								
								//Check which is newer, trello or local
								Log.d("TrelloController - sync", "Local Time:" + dateFormater.format(localCard.getChangedDate()));
								Log.d("TrelloController - sync", "Trello Time:" + dateFormater.format(card.getChangedDate()));
								
								if(localCard.getChangedDate().before(card.getChangedDate())){
									// Trello was edited last, update local to trello
									//Overwrite local if different
									Log.d("TrelloController - sync", "Update local");
									syncController.updateCard(localCard, card);
								} else {
									// Local was edited last or at same second, update trello to local
									if(localCard.getListId().length() != 0){
										//Overwrite trello
										Boolean result = UpdateCardOnTrello(localCard);
										if(result){
											syncController.setCardLocalChanges(localCard, false);
										} else {
											//If failed to overwrite
											Log.d("TrelloController - sync", "Failed to update card on trello");
											syncController.setCardLocalChanges(localCard, true);
										}
									}
								}
							} else {
								//Overwrite local if different
								syncController.updateCard(localCard, card);
							}
						} else {
							//New card on trello
							//Try to convert add to local if converts success
							syncController.addCard(card);
						}
					}
					
					//Add cards from local to trello that aren't on trello yet and are on this board
					//Update cards from local that aren't in trello list
					//Get again, some of trello's may have replaced our local copies without id's
					localCards = syncController.getLocalCards();
					for(int j=0; j < localCards.size(); j++){
						ICard localCard = localCards.get(j);
						if(localCard.getBoardId().contentEquals(localBoard.getTrelloId())){
							if(localCard.getTrelloId().length() == 0){
								//Add card to trello
								Log.d("TrelloController - sync", "Adding card on trello");
								String newId = AddCardToTrello(localCard);
								syncController.setCardLocalChanges(localCard, false);
								if(newId != null){
									syncController.setCardTrelloId(localCard, newId);
								} else {
									syncController.setCardLocalChanges(localCard, true);
								}
							} else if(localCard.hasLocalChanges()) {
								//Check if found in trello
								Boolean found = false;
								for(int i=0; i < trelloCards.size(); i++){
									if(trelloCards.get(i).getTrelloId().contentEquals(localCard.getTrelloId())){
										found = true;
									}
								}
								if(found == false){
									//Overwrite trello
									syncController.setCardLocalChanges(localCard, false);
									Boolean result = UpdateCardOnTrello(localCard);
									if(result == false){
										//If failed to overwrite
										Log.d("TrelloController - sync", "Failed to update card on trello");
										syncController.setCardLocalChanges(localCard, true);
									}
								}
							}
						}
					}
				}
			}
		}
		return internetFailed;
	}
	
	private Boolean addMemberToBoard(String memberId, String boardId){
		Boolean success = true;
		HttpClient client = new DefaultHttpClient();
		List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
		results.add(new BasicNameValuePair("key",TrelloKey));
		results.add(new BasicNameValuePair("token",TrelloToken));
		//Add to board
		HttpPut put = new HttpPut("https://api.trello.com/1/boards/" + boardId + "/members/" + memberId);
		results.add(new BasicNameValuePair("type","normal"));
		try {
			String result = "";
			try {
				put.setEntity(new UrlEncodedFormEntity(results));
				HttpResponse response = client.execute(put);
				if(response != null){
					InputStream is = response.getEntity().getContent(); 
					result = convertStreamToString(is);
				} else {
					Log.d("TrelloController - addMemberToBoard", "No internet data");
					success = false;
				}
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.d("TrelloController - addMemberToBoard", "Add Response:" + result);
		} catch (Exception e) {
			Log.e("TrelloController - addMemberToBoard","client protocol exception", e);
			success = false;
		}
		return success;
	}
	private Boolean removeMemberFromBoard(String memberId, String boardId){
		Boolean success = true;
		HttpClient client = new DefaultHttpClient();
		HttpDeleteWithBody delete;
		
		List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
		results.add(new BasicNameValuePair("key",TrelloKey));
		results.add(new BasicNameValuePair("token",TrelloToken));
		delete = new HttpDeleteWithBody("https://api.trello.com/1/boards/" + boardId + "/members/" + memberId);
		try {
			String result = "";
			try {
				delete.setEntity(new UrlEncodedFormEntity(results));
				HttpResponse response = client.execute(delete);
				if(response != null){
					InputStream is = response.getEntity().getContent(); 
					result = convertStreamToString(is);
				} else {
					Log.d("TrelloController - removeMemberFromBoard", "No internet data");
					success = false;
				}
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.d("TrelloController - removeMemberFromBoard", "Remove Response:" + result);
			if(result.contains("Not enough admins")){
				Log.d("TrelloController - removeMemberFromBoard", "Failed to remove");
				success = false;
			}
		} catch (Exception e) {
			Log.e("TrelloController - removeMemberFromBoard","client protocol exception", e);
			success = false;
		}
		return success;
	}
	private Boolean UpdateCardOnTrello(ICard theCard){
		Log.d("TrelloController - UpdateCardOnTrello", "Called");
		HttpClient client = new DefaultHttpClient();
		HttpPut put = new HttpPut("https://api.trello.com/1/cards/" + theCard.getTrelloId());

		List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
		results.add(new BasicNameValuePair("key",TrelloKey));
		results.add(new BasicNameValuePair("token",TrelloToken));
		
		results.add(new BasicNameValuePair("name", theCard.getName()));
		results.add(new BasicNameValuePair("desc", theCard.getDesc()));
		results.add(new BasicNameValuePair("idList", theCard.getListId()));
		results.add(new BasicNameValuePair("closed", Boolean.toString(theCard.getClosed())));
		
		JSONArray jsonLabels = null;
		if(theCard.getLabels() != null){
			List<String> labels = theCard.getLabels();
			List<String> labelNames = theCard.getLabelNames();
			
			jsonLabels = new JSONArray();
			for(int l = 0; l < labels.size(); l++){
				JSONObject jsonLabel = new JSONObject();
				try {
					jsonLabel.put("color", labels.get(l));
					if(labelNames != null){
						jsonLabel.put("name", labelNames.get(l));
					} else {
						jsonLabel.put("name", "");
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
				jsonLabels.put(jsonLabel);
			}
		}
		if(jsonLabels != null) results.add(new BasicNameValuePair("labels", jsonLabels.toString()));
		
		try {
			String result = "";
			try {
				put.setEntity(new UrlEncodedFormEntity(results));
				HttpResponse response = client.execute(put);
				// Error here if no Internet TODO
				InputStream is = response.getEntity().getContent(); 
				result = convertStreamToString(is);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.d("UpdateCardOnTrello", "Update Response:" + result);
		} catch (Exception e) {
			// Auto-generated catch block
			Log.e("Log Thread","client protocol exception", e);
		}
		return true; //TODO return false on failure
	}
	private String AddCardToTrello(ICard theCard){		
		Log.d("TrelloController - AddCardToTrello", "Called");
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost("https://api.trello.com/1/cards");
		List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
		
		results.add(new BasicNameValuePair("key",TrelloKey));
		results.add(new BasicNameValuePair("token",TrelloToken));
		results.add(new BasicNameValuePair("idList", theCard.getListId()));
		
		if(theCard.getName() != null) results.add(new BasicNameValuePair("name", theCard.getName()));
		if(theCard.getDesc() != null) results.add(new BasicNameValuePair("desc", theCard.getDesc()));

		JSONArray jsonLabels = null;
		if(theCard.getLabels() != null){
			List<String> labels = theCard.getLabels();
			List<String> labelNames = theCard.getLabelNames();
			
			jsonLabels = new JSONArray();
			for(int l = 0; l < labels.size(); l++){
				JSONObject jsonLabel = new JSONObject();
				try {
					jsonLabel.put("color", labels.get(l));
					if(labelNames != null){
						jsonLabel.put("name", labelNames.get(l));
					} else {
						jsonLabel.put("name", "");
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
				jsonLabels.put(jsonLabel);
			}
		}
		if(jsonLabels != null) results.add(new BasicNameValuePair("labels", jsonLabels.toString()));
		
		if(jsonLabels != null) Log.d("Labels:", jsonLabels.toString());
		
		String newId = null;
		try {
			post.setEntity(new UrlEncodedFormEntity(results));
		} catch (UnsupportedEncodingException e) {
			Log.e("AddCardToTrello","An error has occurred", e);
		}
		try {
			HttpResponse response = client.execute(post);
			String result = "";
			try {
				// Error here if no Internet TODO
				InputStream is = response.getEntity().getContent(); 
				result = convertStreamToString(is);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			JSONObject json;
			try {
				json = new JSONObject(result);
				newId = json.getString("id");
			} catch (JSONException e) {
				e.printStackTrace();
			}
		} catch (ClientProtocolException e) {
			Log.e("AddCardToTrello","client protocol exception", e);
		} catch (IOException e) {
			Log.e("AddCardToTrello", "io exception", e);
		}
		return newId; //TODO return null on failure
	}
	
	private String AddListToTrello(IList theList){
		Log.d("TrelloController - AddListToTrello", "Called");
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost("https://api.trello.com/1/lists");
		List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
		
		results.add(new BasicNameValuePair("key",TrelloKey));
		results.add(new BasicNameValuePair("token",TrelloToken));
		
		results.add(new BasicNameValuePair("idBoard", theList.getBoardId()));
		if(theList.getName() != null) results.add(new BasicNameValuePair("name", theList.getName()));
		
		String newId = null;
		try {
			post.setEntity(new UrlEncodedFormEntity(results));
		} catch (UnsupportedEncodingException e) {
			Log.e("AddListToTrello","An error has occurred", e);
		}
		try {
			HttpResponse response = client.execute(post);
			String result = "";
			try {
				// Error here if no internet TODO
				InputStream is = response.getEntity().getContent(); 
				result = convertStreamToString(is);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			Log.d("AddListToTrello", "Add Response:" + result);
			JSONObject json;
			try {
				json = new JSONObject(result);
				newId = json.getString("id");
			} catch (JSONException e) {
				e.printStackTrace();
			}
		} catch (ClientProtocolException e) {
			Log.e("AddListToTrello","client protocol exception", e);
		} catch (IOException e) {
			Log.e("AddListToTrello", "io exception", e);
		}
		return newId; //TODO return null on failure
	}
	
	private Boolean UpdateListOnTrello(IList theList){
		Log.d("TrelloController - UpdateListOnTrello", "Called");
		HttpClient client = new DefaultHttpClient();
		HttpPut put = new HttpPut("https://api.trello.com/1/lists/" + theList.getTrelloId());

		List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
		results.add(new BasicNameValuePair("key",TrelloKey));
		results.add(new BasicNameValuePair("token",TrelloToken));
		
		results.add(new BasicNameValuePair("name", theList.getName()));
		results.add(new BasicNameValuePair("idBoard", theList.getBoardId()));

		try {
			String result = "";
			try {
				put.setEntity(new UrlEncodedFormEntity(results));
				HttpResponse response = client.execute(put);
				InputStream is = response.getEntity().getContent(); // Error here if no Internet TODO
				result = convertStreamToString(is);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.d("UpdateListOnTrello", "Update Response:" + result);
		} catch (Exception e) {
			// Auto-generated catch block
			Log.e("UpdateListOnTrello","client protocol exception", e);
		}
		return true; //TODO return false on failure
	}
	private Boolean UpdateBoardOnTrello(IBoard theBoard){
		Log.d("TrelloController - UpdateBoardOnTrello", "Called");
		HttpClient client = new DefaultHttpClient();
		HttpPut put = new HttpPut("https://api.trello.com/1/boards/" + theBoard.getTrelloId());

		List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
		results.add(new BasicNameValuePair("key",TrelloKey));
		results.add(new BasicNameValuePair("token",TrelloToken));
		
		results.add(new BasicNameValuePair("name", theBoard.getName()));
		results.add(new BasicNameValuePair("desc", theBoard.getDesc()));

		try {
			String result = "";
			try {
				put.setEntity(new UrlEncodedFormEntity(results));
				HttpResponse response = client.execute(put);
				InputStream is = response.getEntity().getContent(); // Error here if no Internet TODO
				result = convertStreamToString(is);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.d("UpdateBoardOnTrello", "Update Response:" + result);
		} catch (Exception e) {
			// Auto-generated catch block
			Log.e("UpdateBoardOnTrello","client protocol exception", e);
		}
		return true; //TODO return false on failure
	}
	private String AddBoardToTrello(IBoard theBoard){
		Log.d("TrelloController - AddBoardToTrello", "Called");
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost("https://api.trello.com/1/boards");
		
		List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
		results.add(new BasicNameValuePair("key",TrelloKey));
		results.add(new BasicNameValuePair("token",TrelloToken));
		results.add(new BasicNameValuePair("idOrganization",OrganizationID));
		
		if(theBoard.getName() != null) results.add(new BasicNameValuePair("name", theBoard.getName()));
		if(theBoard.getDesc() != null) results.add(new BasicNameValuePair("desc", theBoard.getDesc()));
		results.add(new BasicNameValuePair("prefs_permissionLevel", "org"));

		String newId = null;
		try {
			post.setEntity(new UrlEncodedFormEntity(results));
		} catch (UnsupportedEncodingException e) {
			// Auto-generated catch block
			Log.e("AddBoardToTrello","An error has occurred", e);
		}
		try {
			HttpResponse response = client.execute(post);
			String result = "";
			try {
				//TODO Error here if no Internet
				InputStream is = response.getEntity().getContent(); 
				result = convertStreamToString(is);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			Log.d("AddBoardToTrello", "Add Response:" + result);
			JSONObject json;
			try {
				json = new JSONObject(result);
				newId = json.getString("id");
			} catch (JSONException e) {
				e.printStackTrace();
			}
		} catch (ClientProtocolException e) {
			Log.e("AddBoardToTrello","client protocol exception", e);
		} catch (IOException e) {
			Log.e("AddBoardToTrello", "io exception", e);
		}
		
		if(newId != null){
			//Add all members to this board
			SharedPreferences settings = AppContext.getSharedPreferences(PREFS_NAME, 0);
			Set<String> set = settings.getStringSet("organizationMembers", null);
			if(set != null){
				String[] setArray = (String[]) set.toArray(new String[set.size()]);
				for(int i=0; i< setArray.length; i++){
					addMemberToBoard(setArray[i], newId);
				}
			}
		}
		return newId; //TODO return null on failure
	}
	
	private List<TrelloBoard> getBoards(){
		Log.d("TrelloController - getBoards", "Called");
		List<TrelloBoard> boardsList = new  ArrayList<TrelloBoard>();
		String url = "https://api.trello.com/1/organizations/" + OrganizationID + "/boards?key=" + TrelloKey + "&token=" + TrelloToken + "&filter=open&fields=name,desc";
		
		HttpResponse response = getData(url);
		if(response != null){
			String result = "";
			try {
				//Error here if no Internet
				InputStream is = response.getEntity().getContent(); 
				result = convertStreamToString(is);
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//Log.d("getBoards", "Result:" + result);
			JSONArray json = null;
			try {
				json = new JSONArray(result);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
					
			// Loop through boards from trello
			for (int i = 0; i < json.length(); i++) {
				
				JSONObject jsonBoard = null;
				
				String trello_id = "";
				String name = "";
				String desc = "";
				
				try {
					jsonBoard = json.getJSONObject(i);
					trello_id = jsonBoard.getString("id");
					name = jsonBoard.getString("name");
					desc = jsonBoard.getString("desc");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				
				TrelloBoard newBoard = new TrelloBoard(trello_id, name, desc, false);
				boardsList.add(newBoard);
			}
			return boardsList;
		} else {
			Log.d("TrelloController - getBoards", "No internet data");
			return null;
		}
	}

	class TrelloAction {
    	String type;
    	String id;
    	Date date;
		public TrelloAction(String type, String id, Date date) {
			super();
			this.type = type;
			this.id = id;
			this.date = date;
		}
		public String getType() {
			return type;
		}
		public String getId() {
			return id;
		}
		public Date getDate() {
			return date;
		}
		public void setType(String type) {
			this.type = type;
		}
		public void setId(String id) {
			this.id = id;
		}
		public void setDate(Date date) {
			this.date = date;
		}
		
    }
	
	private TrelloBoard getListsAndCards(String boardId){
		Log.d("TrelloController - getListsAndCards", "Called");
		
		SharedPreferences settings = AppContext.getSharedPreferences(PREFS_NAME, 0);
	    String dateLastSync = settings.getString("dateLastSync", null);
		
		//String url = "https://api.trello.com/1/boards/" + boardId + "?key=" + TrelloKey + "&token=" + TrelloToken + "&fields=name,desc,closed&cards=all&card_fields=idList,name,desc,labels,closed&lists=all&list_fields=name,closed";
		String url = "https://api.trello.com/1/boards/" + boardId + "?key=" + TrelloKey + "&token=" + TrelloToken + "&fields=name,desc,closed&lists=all&list_fields=name,closed&actions=createCard,updateCard,commentCard,addAttachmentToCard,moveCardFromBoard,moveCardToBoard&action_memberCreator=false&action_fields=data,type,date&action_member=false";

	    if(dateLastSync != null){
	    	dateLastSync = dateLastSync.replace(" ", "T");
	    	dateLastSync = dateLastSync + ".000Z";
	    	url = url + "&actions_since=" + dateLastSync;
	    }
		//Log.d("TrelloController - getListsAndCards", "URL Actions:" + url);

	    
		//2013-03-29T11:22:30.368Z
	    
	    SharedPreferences.Editor editor = settings.edit();
		//2013-03-29 11:22:30
	    editor.putString("dateLastSync", dateFormater.format(new Date()));
	    editor.commit();
	    
		reportSyncTime(dateFormaterReport.format(new Date()));
		
		HttpResponse response = getData(url);
		if(response == null) return null;
		String result = "";
		try {
			//Error here if no Internet
			InputStream is = response.getEntity().getContent(); 
			result = convertStreamToString(is);
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//Log.d("Result:", result);
		JSONObject board = null;
		String board_id = "";
		String board_name = "";
		String board_desc = "";
		Boolean closed = false;
		
		JSONArray actions = null;
		JSONArray lists = null;
		try {
			board = new JSONObject(result);
			board_id = board.getString("id");
			board_name = board.getString("name");
			board_desc = board.getString("desc");
			closed = board.getBoolean("closed");
			actions = board.getJSONArray("actions");
			lists = board.getJSONArray("lists");
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		List <TrelloAction> trelloActions = new ArrayList<TrelloAction>(); 
		// Loop through actions from trello
		for (int i = 0; i < actions.length(); i++) {
			//Log.d("TrelloController - getListsAndCards", "Action #" + Integer.toString(i));
			JSONObject jsonAction = null;
			String action_type = "";
			String action_date = "";
			JSONObject action_data = null;
			JSONObject card = null;
			String card_id = "";
			
			try {
				jsonAction = actions.getJSONObject(i);
				action_type = jsonAction.getString("type");
				action_date = jsonAction.getString("date");
				action_data = jsonAction.getJSONObject("data");
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			//Get id of card
			try {
				card = action_data.getJSONObject("card");
				card_id = card.getString("id");
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			//Convert action_date to date
			//2013-03-29T11:22:30.368Z
			//Log.d("TrelloController - getListsAndCards", "Old Action Date:" + action_date);
			action_date = action_date.replace("T", " ");
			action_date = action_date.substring(0, action_date.length() - 5);
			//Log.d("TrelloController - getListsAndCards", "New Action Date:" + action_date);
			
			Date date;
			try {
				date = dateFormater.parse(action_date);
			} catch (ParseException e) {
				date = new Date(0);
			}
			
			//Get card with this id from trello
			trelloActions.add(new TrelloAction(action_type, card_id, date));
		}
		if(trelloActions.size() != 0){
			//Query trello search api to get cards with these id's
			url = "https://api.trello.com/1/search?key=" + TrelloKey + "&token=" + TrelloToken + "&query=is:open&modelTypes=cards&card_fields=idBoard,idList,name,desc,labels&cards_limit=1000&idCards=";
			for(int j=0; j<trelloActions.size(); j++){
		    	url = url.concat(trelloActions.get(j).getId() + ","); //Remove last comma
		    }
			url = url.substring(0, url.length() - 1);
			
			Log.d("Get Cards URL:" , url);

			//Log.d("TrelloController - getListsAndCards", "URL Search:" + url);
			response = getData(url);
			result = "";
			try {
				//Error here if no Internet
				InputStream is = response.getEntity().getContent(); 
				result = convertStreamToString(is);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			JSONObject search = null;
			JSONArray cards = null;
			try {
				search = new JSONObject(result);
				cards = search.getJSONArray("cards");
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			// Loop through these open cards from trello, remove from actions when found
			for (int k = 0; k < cards.length(); k++) {
				JSONObject jsonCard = null;
				String card_id = "";
				String card_idList = "";
				String card_name = "";
				String card_desc = "";
				JSONArray jsonLabels = null;
				
				List<String> card_labels = new ArrayList<String>();
				List<String> card_labelNames = new ArrayList<String>();
				
				try {
					jsonCard = cards.getJSONObject(k);
					card_id = jsonCard.getString("id");
					card_idList = jsonCard.getString("idList");
					card_name = jsonCard.getString("name");
					card_desc = jsonCard.getString("desc");
					jsonLabels = jsonCard.getJSONArray("labels");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				
				for(int l = 0; l < jsonLabels.length(); l++){
					JSONObject jsonLabel = null;
					try {
						jsonLabel = jsonLabels.getJSONObject(l);
						card_labels.add(jsonLabel.getString("color"));
						card_labelNames.add(jsonLabel.getString("name"));
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				//Find this card in the trelloActions, get its date, and remove it from trelloActions (could be multiple actions on one card)
				Date newestDate = null;
				Iterator<TrelloAction> iter = trelloActions.iterator();
				while(iter.hasNext()){
					TrelloAction curAction = iter.next();
					Log.d("TrelloController - getListsAndCards", "Removing actions");
					if(curAction.getId().contentEquals(card_id)){
						if(newestDate == null || newestDate.before(curAction.getDate())){
							newestDate = curAction.getDate();
						}
						Log.d("TrelloController - getListsAndCards", "Removing:" + curAction.getId());
						iter.remove(); //Remove this action from trelloActions
					}
				}
				TrelloCard newCard = new TrelloCard(card_id, card_idList, board_id, card_name, card_desc, card_labelNames, card_labels, false, newestDate);
				trelloCards.add(newCard);
			}
			
			//Any actions left in trelloActions correspond cards which have been removed
			List<String> alreadyAdded = new ArrayList<String>();
			Iterator<TrelloAction> iter = trelloActions.iterator();
			while(iter.hasNext()){
				TrelloAction curAction = iter.next();
				//Log.d("TrelloController - getListsAndCards", "Actions left!");
				//Log.d("TrelloController - getListsAndCards", "Actions left:" + curAction.getId());

				if(alreadyAdded.contains(curAction.getId()) == false){
					alreadyAdded.add(curAction.getId());
					
					//Get newest date
					Date newestDate = null;
					Iterator<TrelloAction> iter2 = trelloActions.iterator();
					while(iter2.hasNext()){
						TrelloAction curAction2 = iter2.next();
						if(curAction2.getId().contentEquals(curAction.getId())){
							if(newestDate == null || newestDate.before(curAction2.getDate())){
								newestDate = curAction2.getDate();
							}
						}
					}
					TrelloCard newCard = new TrelloCard(curAction.getId(), "", "", "", "", null, null, true, newestDate);
					trelloCards.add(newCard);
				}
			}
		}
		
		// Loop through lists from trello
		for (int i = 0; i < lists.length(); i++) {
			JSONObject jsonList = null;
			String list_id = "";
			String list_name = "";
			Boolean list_closed = false;
			try {
				jsonList = lists.getJSONObject(i);
				list_id = jsonList.getString("id");
				list_name = jsonList.getString("name");
				list_closed = jsonList.getBoolean("closed");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			TrelloList newList = new TrelloList(list_id, board_id, list_name, list_closed);
			trelloLists.add(newList);
		}
		return new TrelloBoard(board_id, board_name, board_desc, closed);
	}
	
	public static String convertStreamToString(InputStream inputStream)
			throws IOException {
		if (inputStream != null) {
			Writer writer = new StringWriter();

			char[] buffer = new char[1024];
			try {
				Reader reader = new BufferedReader(new InputStreamReader(
						inputStream, "UTF-8"), 1024);
				int n;
				while ((n = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, n);
				}
			} finally {
				inputStream.close();
			}
			return writer.toString();
		} else {
			return "";
		}
	}
	public static HttpResponse getData(String url) {
		HttpResponse response = null;
		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();
			request.setURI(new URI(url));
			response = client.execute(request);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return response;
	}
	class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
	    public static final String METHOD_NAME = "DELETE";
	    public String getMethod() { return METHOD_NAME; }

	    public HttpDeleteWithBody(final String uri) {
	        super();
	        setURI(URI.create(uri));
	    }
	    public HttpDeleteWithBody(final URI uri) {
	        super();
	        setURI(uri);
	    }
	    public HttpDeleteWithBody() { super(); }
	}
	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager 
	          = (ConnectivityManager) this.AppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}
}
