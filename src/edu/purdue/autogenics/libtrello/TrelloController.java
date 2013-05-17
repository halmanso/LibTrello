package edu.purdue.autogenics.libtrello;

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
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

//Singleton
//Handles syncing of all boards/lists/cards
public class TrelloController {
	private String OrganizationID;
	private String TrelloKey;
	private String TrelloToken;
	
	public static final String PREFS_NAME = "TrelloClient";
	
	private Boolean started = false;
	
	List <TrelloBoard> trelloBoards;
	List <TrelloList> trelloLists;
	List <TrelloCard> trelloCards; 
	
	public TrelloController(String organizationID, String trelloKey, String trelloToken) {
		super();
		OrganizationID = organizationID;
		TrelloKey = trelloKey;
		TrelloToken = trelloToken;
	}

	public void sync(ISyncController syncController){
		trelloLists = new ArrayList<TrelloList>();
		trelloCards = new ArrayList<TrelloCard>();	

		List<ICard> localCards;
		List<IList> localLists;
		List<IBoard> localBoards;
		Boolean addedBoards = false;
		
		localBoards = syncController.getLocalBoards();
		if(started == false){
			//Download all boards from Trello
			trelloBoards = getBoards();
			
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
			started = true;
		}
		
		if(addedBoards){
			//Get again, trello id changed
			localBoards = syncController.getLocalBoards();
		}
		for(int h=0; h<localBoards.size(); h++){
			IBoard localBoard = localBoards.get(h);
			
			if(localBoard.getTrelloId().length() != 0){
				//Sync Cards and Lists of all localBoards with trelloIds
				
				TrelloBoard trelloBoard = getListsAndCards(localBoard.getTrelloId()); //TODO receive board here, check name/closed
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
				
				//TODO handle Board Rename
				
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
				
				//**** CARDS SYNC ****
				//Loop through cards and update accordingly
				localCards = syncController.getLocalCards();
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
							if(localCard.getListId().length() != 0){
								//Overwrite trello
								syncController.setCardLocalChanges(localCard, false);
								Boolean result = UpdateCardOnTrello(localCard);
								if(result == false){
									//If failed to overwrite
									Log.d("TrelloController - sync", "Failed to update card on trello");
									syncController.setCardLocalChanges(localCard, true);
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
				//Get again, some of trello's may have replaced our local copies without id's
				localCards = syncController.getLocalCards();
				for(int j=0; j < localCards.size(); j++){
					ICard localCard = localCards.get(j);
					if(localCard.getTrelloId().length() == 0 && localCard.getBoardId().contentEquals(localBoard.getTrelloId())){
						//Add card to trello
						String newId = AddCardToTrello(localCard);
						syncController.setCardLocalChanges(localCard, false);
						if(newId != null){
							syncController.setCardTrelloId(localCard, newId);
						} else {
							syncController.setCardLocalChanges(localCard, true);
						}
					}
				}
			}
		}
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
		return newId; //TODO return null on failure
	}
	
	private List<TrelloBoard> getBoards(){
		Log.d("TrelloController - getBoards", "Called");
		List<TrelloBoard> boardsList = new  ArrayList<TrelloBoard>();
		String url = "https://api.trello.com/1/organizations/" + OrganizationID + "/boards?key=" + TrelloKey + "&token=" + TrelloToken + "&filter=open&fields=name,desc";
		
		HttpResponse response = getData(url);
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
	}

	
	private TrelloBoard getListsAndCards(String boardId){
		Log.d("TrelloController - getListsAndCards", "Called");
		String url = "https://api.trello.com/1/boards/" + boardId + "?key=" + TrelloKey + "&token=" + TrelloToken + "&fields=name,desc,closed&cards=all&card_fields=idList,name,desc,labels,closed&lists=all&list_fields=name,closed";
		
		HttpResponse response = getData(url);
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
		
		
		//TODO check if name changed or closed
		
		JSONArray cards = null;
		JSONArray lists = null;
		try {
			board = new JSONObject(result);
			board_id = board.getString("id");
			board_name = board.getString("name");
			board_desc = board.getString("desc");
			closed = board.getBoolean("closed");
			cards = board.getJSONArray("cards");
			lists = board.getJSONArray("lists");
		} catch (JSONException e) {
			e.printStackTrace();
		}
				
		// Loop through cards from trello
		for (int i = 0; i < cards.length(); i++) {
			JSONObject jsonCard = null;
			String card_id = "";
			String card_idList = "";
			String card_name = "";
			String card_desc = "";
			Boolean card_closed = false;
			JSONArray jsonLabels = null;
			
			List<String> card_labels = new ArrayList<String>();
			List<String> card_labelNames = new ArrayList<String>();
			
			try {
				jsonCard = cards.getJSONObject(i);
				card_id = jsonCard.getString("id");
				card_idList = jsonCard.getString("idList");
				card_name = jsonCard.getString("name");
				card_desc = jsonCard.getString("desc");
				jsonLabels = jsonCard.getJSONArray("labels");
				card_closed = jsonCard.getBoolean("closed");
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			for(int j = 0; j < jsonLabels.length(); j++){
				JSONObject jsonLabel = null;
				try {
					jsonLabel = jsonLabels.getJSONObject(i);
					card_labels.add(jsonLabel.getString("color"));
					card_labelNames.add(jsonLabel.getString("name"));
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			TrelloCard newCard = new TrelloCard(card_id, card_idList, board_id, card_name, card_desc, card_labelNames, card_labels, card_closed);
			trelloCards.add(newCard);
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
}
