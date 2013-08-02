package com.openatk.libtrello;
import java.util.Date;
import java.util.List;


public class TrelloCard implements ICard {
	String id;
	String listId;
	String boardId;
	String name;
	String desc;
	List<String> labelNames;
	List<String> labels;
	Boolean closed;
	Date changedDate;
	
	public TrelloCard(String id, String listId, String boardId, String name,
			String desc, List<String> labelNames, List<String> labels, Boolean closed, Date changedDate) {
		super();
		this.id = id;
		this.listId = listId;
		this.boardId = boardId;
		this.name = name;
		this.desc = desc;
		this.labelNames = labelNames;
		this.labels = labels;
		this.closed = closed;
		this.changedDate = changedDate;
	}

	@Override
	public String getBoardId() {
		return boardId;
	}
	@Override
	public String getTrelloId() {
		return id;
	}
	@Override
	public Boolean getClosed() {
		return closed;
	}
	@Override
	public String getListId() {
		return listId;
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
	public List<String> getLabelNames() {
		return labelNames;
	}
	@Override
	public List<String> getLabels() {
		return labels;
	}
	@Override
	public Boolean hasLocalChanges() {
		return null;
	}
	@Override
	public void setLocalChanges(Boolean changes) {
		//Unused
	}
	@Override
	public Object getLocalId() {
		//Unused
		return null;
	}
	@Override
	public Date getChangedDate() {
		return this.changedDate;
	}

	public void setTrelloId(String id) {
		this.id = id;
	}
	public void setBoardId(String boardId) {
		this.boardId = boardId;
	}
	public void setListId(String listId) {
		this.listId = listId;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public void setLabelNames(List<String> labelNames) {
		this.labelNames = labelNames;
	}
	public void setLabels(List<String> labels) {
		this.labels = labels;
	}
	public void setChangedDate(Date changedDate) {
		this.changedDate = changedDate;
	}
	
	
}
