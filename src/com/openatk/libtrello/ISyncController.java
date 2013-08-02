package com.openatk.libtrello;

import java.util.List;

public interface ISyncController {
	public void changeOrganization();
	
	public void updateBoard(IBoard localBoard, IBoard trelloBoard);
	public void addBoard(IBoard trelloBoard);
	public void setBoardTrelloId(IBoard localBoard, String newId);
	public void setBoardLocalChanges(IBoard localBoard, Boolean changes);
	
	public void updateList(IList localList, IList trelloList);
	public void addList(IList trelloList);
	public void setListTrelloId(IList localList, String newId);
	public void setListLocalChanges(IList localList, Boolean changes);
	
	public void updateCard(ICard localCard, ICard trelloCard); //If closed card, only cardId is valid
	public void addCard(ICard trelloCard);  //If closed card, only cardId is valid
	public void setCardTrelloId(ICard localCard, String newId);
	public void setCardLocalChanges(ICard localCard, Boolean changes);
	
	public List<ICard> getLocalCards();
	public List<IList> getLocalLists();
	public List<IBoard> getLocalBoards();
	
	public void finishedSync();
}
