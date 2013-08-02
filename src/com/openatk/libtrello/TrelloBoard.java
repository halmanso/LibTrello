package com.openatk.libtrello;



//Shared
public class TrelloBoard implements IBoard {
	private String id;
	private String name;
	private String desc;
	private Boolean closed;
	
	public TrelloBoard(String id, String name, String desc, Boolean closed) {
		super();
		this.id = id;
		this.name = name;
		this.desc = desc;
		this.closed = closed;
	}
	
	@Override
	public String getTrelloId() {
		return id;
	}
	@Override
	public String getName() {
		return name;
	}
	@Override
	public String getDesc() {
		return desc;
	}
	@Override
	public Boolean hasLocalChanges() {
		return null;
	}
	@Override
	public void setLocalChanges(Boolean changes) {
		
	}
	@Override
	public Boolean getClosed() {
		return closed;
	}
	@Override
	public Object getLocalId() {
		//Unused
		return null;
	}
	

	public void setTrelloId(String id) {
		this.id = id;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public void setClosed(Boolean closed) {
		this.closed = closed;
	}

	
}
