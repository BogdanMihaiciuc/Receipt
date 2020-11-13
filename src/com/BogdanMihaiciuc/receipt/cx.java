package com.BogdanMihaiciuc.receipt;

import java.io.Serializable;

@Deprecated
public class cx implements Serializable {
	
	private static final long serialVersionUID = 0L;
	
	String name;
	long qty;
	long price;
	
	boolean crossedOff;
	int controlFlags;
	
	long estimatedPrice;
	String measurementUnit;
	
	public static  ItemListFragment.Item convert(cx i) {
		ItemListFragment.Item item = new ItemListFragment.Item();
		item.name = i.name;
		item.qty = i.qty * 100;
		item.price = i.price;
		item.crossedOff = i.crossedOff;
		item.flags = i.controlFlags;
		item.estimatedPrice = i.estimatedPrice;
		item.unitOfMeasurement = i.measurementUnit;
		return item;
	}
	
}
