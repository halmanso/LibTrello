package com.openatk.libtrello;

public class TrelloList implements IList {
	String id;
	String boardId;
	String name;
	Boolean closed;
	
	public TrelloList(String id, String boardId, String name, Boolean closed) {
		super();
		this.id = id;
		this.boardId = boardId;
		this.name = name;
		this.closed = closed;
	}
	
	@Override
	public String getTrelloId() {
		return id;
	}
	@Override
	public String getBoardId() {
		return boardId;
	}
	@Override
	public String getName() {
		return name;
	}
	@Override
	public Boolean getClosed() {
		return closed;
	}
	@Override
	public Boolean hasLocalChanges() {
		return null;
	}
	@Override
	public void setLocalChanges(Boolean changes) {
		
	}
	@Override
	public Object getLocalId() {
		//Unused
		return null;
	}

	public void setBoardId(String boardId) {
		this.boardId = boardId;
	}
	public void setTrelloId(String id) {
		this.id = id;
	}
	public void setName(String name) {
		this.name = name;
	}
}
