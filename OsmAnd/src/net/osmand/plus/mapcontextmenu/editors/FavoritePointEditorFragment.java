package net.osmand.plus.mapcontextmenu.editors;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;

import net.osmand.data.FavouritePoint;
import net.osmand.data.PointDescription;
import net.osmand.plus.FavouritesDbHelper;
import net.osmand.plus.FavouritesDbHelper.FavoriteGroup;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.FavoriteImageDrawable;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.util.Algorithms;

public class FavoritePointEditorFragment extends PointEditorFragment {

	private FavoritePointEditor editor;
	private FavouritePoint favorite;
	private FavoriteGroup group;
	FavouritesDbHelper helper;

	private boolean saved;
	private int defaultColor;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		helper = getMyApplication().getFavorites();
		editor = getMapActivity().getFavoritePointEditor();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		boolean light = getMyApplication().getSettings().isLightContent();
		defaultColor = light ? R.color.icon_color : R.color.icon_color_light;

		favorite = editor.getFavorite();
		group = helper.getGroup(favorite);
	}

	@Override
	public PointEditor getEditor() {
		return editor;
	}

	@Override
	public String getToolbarTitle() {
		if (editor.isNew()) {
			return getMapActivity().getResources().getString(R.string.favourites_context_menu_add);
		} else {
			return getMapActivity().getResources().getString(R.string.favourites_context_menu_edit);
		}
	}

	@Override
	public void setCategory(String name) {
		group = helper.getGroup(name);
		super.setCategory(name);
	}

	public static void showInstance(final MapActivity mapActivity) {
		FavoritePointEditor editor = mapActivity.getFavoritePointEditor();
		//int slideInAnim = editor.getSlideInAnimation();
		//int slideOutAnim = editor.getSlideOutAnimation();

		FavoritePointEditorFragment fragment = new FavoritePointEditorFragment();
		mapActivity.getSupportFragmentManager().beginTransaction()
				//.setCustomAnimations(slideInAnim, slideOutAnim, slideInAnim, slideOutAnim)
				.add(R.id.fragmentContainer, fragment, editor.getFragmentTag())
				.addToBackStack(null).commit();
	}

	@Override
	protected boolean wasSaved() {
		return saved;
	}

	@Override
	protected void save(final boolean needDismiss) {
		final FavouritePoint point = new FavouritePoint(favorite.getLatitude(), favorite.getLongitude(),
				getNameTextValue(), getCategoryTextValue());
		point.setDescription(getDescriptionTextValue());
		AlertDialog.Builder builder = FavouritesDbHelper.checkDuplicates(point, helper, getMapActivity());

		if (favorite.getName().equals(point.getName()) &&
				favorite.getCategory().equals(point.getCategory()) &&
				Algorithms.stringsEqual(favorite.getDescription(), point.getDescription())) {

			if (needDismiss) {
				dismiss(true);
			}
			return;
		}

		if (builder != null) {
			builder.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					doSave(favorite, point.getName(), point.getCategory(), point.getDescription(), needDismiss);
				}
			});
			builder.create().show();
		} else {
			doSave(favorite, point.getName(), point.getCategory(), point.getDescription(), needDismiss);
		}
		saved = true;
	}

	private void doSave(FavouritePoint favorite, String name, String category, String description, boolean needDismiss) {
		if (editor.isNew()) {
			doAddFavorite(name, category, description);
		} else {
			helper.editFavouriteName(favorite, name, category, description);
		}
		getMapActivity().getMapView().refreshMap(true);
		if (needDismiss) {
			dismiss(false);
		}

		MapContextMenu menu = getMapActivity().getContextMenu();
		if (menu.getObject() == favorite) {
			PointDescription pointDescription = favorite.getPointDescription();
			pointDescription.setLat(favorite.getLatitude());
			pointDescription.setLon(favorite.getLongitude());
			menu.refreshMenu(pointDescription, favorite);
		}
	}

	private void doAddFavorite(String name, String category, String description) {
		favorite.setName(name);
		favorite.setCategory(category);
		favorite.setDescription(description);
		helper.addFavourite(favorite);
	}

	@Override
	protected void delete(final boolean needDismiss) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(getString(R.string.favourites_remove_dialog_msg, favorite.getName()));
		builder.setNegativeButton(R.string.shared_string_no, null);
		builder.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				helper.deleteFavourite(favorite);
				if (needDismiss) {
					dismiss(true);
				}
				getMapActivity().getMapView().refreshMap(true);
			}
		});
		builder.create().show();
	}

	@Override
	public String getHeaderCaption() {
		return getMapActivity().getResources().getString(R.string.favourites_edit_dialog_title);
	}

	@Override
	public String getNameInitValue() {
		return favorite.getName();
	}

	@Override
	public String getCategoryInitValue() {
		return favorite.getCategory().length() == 0 ? getString(R.string.shared_string_favorites) : favorite.getCategory();
	}

	@Override
	public String getDescriptionInitValue() {
		return favorite.getDescription();
	}

	@Override
	public Drawable getNameIcon() {
		int color = defaultColor;
		if (group != null) {
			color = group.color;
		}
		return FavoriteImageDrawable.getOrCreate(getMapActivity(), color, getMapActivity().getMapView().getCurrentRotatedTileBox().getDensity());
	}

	@Override
	public Drawable getCategoryIcon() {
		int color = defaultColor;
		if (group != null) {
			color = group.color;
		}
		return getIcon(R.drawable.ic_action_folder_stroke, color);
	}

	public Drawable getIcon(int resId, int color) {
		OsmandApplication app = getMyApplication();
		Drawable d = app.getResources().getDrawable(resId).mutate();
		d.clearColorFilter();
		d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
		return d;
	}
}
