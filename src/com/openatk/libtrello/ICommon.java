package com.openatk.libtrello;

public interface ICommon {
	public String getTrelloId();
	public Object getLocalId();
	public Boolean getClosed();
	public Boolean hasLocalChanges();
	public void setLocalChanges(Boolean changes);
}
