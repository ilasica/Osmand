package net.osmand.plus;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;

public class IconsCache {

	private TLongObjectHashMap<Drawable> drawable = new TLongObjectHashMap<Drawable>();
	private OsmandApplication app;
	
	public IconsCache(OsmandApplication app) {
		this.app = app;
	}
	
	
	private Drawable getDrawable(int resId, int clrId) {
		long hash = ((long)resId << 31l) + clrId;
		Drawable d = drawable.get(hash);
		if(d == null) {
			d = app.getResources().getDrawable(resId).mutate();
			d.clearColorFilter();
			if (clrId != 0) {
				d.setColorFilter(app.getResources().getColor(clrId), PorterDuff.Mode.MULTIPLY);
			}
			drawable.put(hash, d);
		}
		return d;
	}
	
	public Drawable getContentIcon(int id, int lightContentColor, int darkContentColor) {
		return getDrawable(id, app.getSettings().isLightContent() ? lightContentColor : darkContentColor);
	}
	
	public Drawable getContentIcon(int id, int lightContentColor) {
		return getDrawable(id, app.getSettings().isLightContent() ? lightContentColor : 0);
	}
	
	public Drawable getIcon(int id, int color) {
		return getDrawable(id, color);
	}
	
	public Drawable getContentIcon(int id) {
		return getDrawable(id, app.getSettings().isLightContent() ? R.color.icon_color_light : 0);
	}

}
