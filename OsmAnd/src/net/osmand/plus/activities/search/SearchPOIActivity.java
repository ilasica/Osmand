/**
 * 
 */
package net.osmand.plus.activities.search;


import gnu.trove.set.hash.TLongHashSet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.osmand.ResultMatcher;
import net.osmand.access.AccessibleToast;
import net.osmand.access.NavigationInfo;
import net.osmand.data.Amenity;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.osm.PoiCategory;
import net.osmand.osm.PoiType;
import net.osmand.plus.OsmAndConstants;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmAndLocationProvider.OsmAndCompassListener;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.activities.EditPOIFilterActivity;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.OsmandListActivity;
import net.osmand.plus.dialogs.DirectionsDialogs;
import net.osmand.plus.poi.NameFinderPoiFilter;
import net.osmand.plus.poi.PoiLegacyFilter;
import net.osmand.plus.poi.SearchByNameFilter;
import net.osmand.plus.render.RenderingIcons;
import net.osmand.plus.views.DirectionDrawable;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;
import net.osmand.util.OpeningHoursParser;
import net.osmand.util.OpeningHoursParser.OpeningHours;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


/**
 * Search poi activity
 */
public class SearchPOIActivity extends OsmandListActivity implements OsmAndCompassListener, OsmAndLocationListener {

	private static final int COMPASS_REFRESH_MSG_ID = OsmAndConstants.UI_HANDLER_SEARCH + 3;
	public static final String AMENITY_FILTER = "net.osmand.amenity_filter"; //$NON-NLS-1$
	public static final String SEARCH_LAT = SearchActivity.SEARCH_LAT; //$NON-NLS-1$
	public static final String SEARCH_LON = SearchActivity.SEARCH_LON; //$NON-NLS-1$
	private static final float MIN_DISTANCE_TO_RESEARCH = 20;
	private static final float MIN_DISTANCE_TO_REFRESH = 5;
	private static final int SEARCH_MORE = 0;
	private static final int SHOW_ON_MAP = 1;
	private static final int FILTER = 2;

	private static final int ORIENTATION_0 = 0;
	private static final int ORIENTATION_90 = 3;
	private static final int ORIENTATION_270 = 1;
	private static final int ORIENTATION_180 = 2;

	private PoiLegacyFilter filter;
	private AmenityAdapter amenityAdapter;
	private EditText searchFilter;
	private View searchFilterLayout;
	
	private boolean searchNearBy = false;
	private net.osmand.Location location = null; 
	private Float heading = null;
	
	private Handler uiHandler;
	private OsmandSettings settings;

	private float width = 24;
	private float height = 24;
	
	// never null represents current running task or last finished
	private SearchAmenityTask currentSearchTask = new SearchAmenityTask(null);
	private OsmandApplication app;
	private MenuItem showFilterItem;
	private MenuItem showOnMapItem;
	private MenuItem searchPOILevel;
	private Button searchFooterButton;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu = getClearToolbar(true).getMenu();
		boolean light = getMyApplication().getSettings().isLightActionBar();
		searchPOILevel = menu.add(0, SEARCH_MORE, 0, R.string.search_POI_level_btn);
		MenuItemCompat.setShowAsAction(searchPOILevel,
				MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		searchPOILevel.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {

				return searchMore();
			}

		});
		updateSearchPoiTextButton(false);
		
		showFilterItem = menu.add(0, FILTER, 0, R.string.search_poi_filter);
		MenuItemCompat.setShowAsAction(showFilterItem,
				MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		showFilterItem = showFilterItem.setIcon(R.drawable.ic_action_filter_dark);
		showFilterItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				if(isSearchByNameFilter()){
					Intent newIntent = new Intent(SearchPOIActivity.this, EditPOIFilterActivity.class);
					newIntent.putExtra(EditPOIFilterActivity.AMENITY_FILTER, PoiLegacyFilter.CUSTOM_FILTER_ID);
					if(location != null) {
						newIntent.putExtra(EditPOIFilterActivity.SEARCH_LAT, location.getLatitude());
						newIntent.putExtra(EditPOIFilterActivity.SEARCH_LON, location.getLongitude());
					}
					startActivity(newIntent);
				} else {
					if (searchFilterLayout.getVisibility() == View.GONE) {
						searchFilterLayout.setVisibility(View.VISIBLE);
					} else {
						searchFilter.setText(""); //$NON-NLS-1$
						searchFilterLayout.setVisibility(View.GONE);
					}
				}
				return true;
			}
		});
		updateShowFilterItem();
		if(isSearchByNameFilter() || isNameFinderFilter()) {
			showFilterItem.setVisible(false);
		}
		

		showOnMapItem = menu.add(0, SHOW_ON_MAP, 0, R.string.shared_string_show_on_map);
		MenuItemCompat.setShowAsAction(showOnMapItem,
				MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		showOnMapItem = showOnMapItem.setIcon(R.drawable.ic_action_map_marker_dark);
		showOnMapItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				if (searchFilter.getVisibility() == View.VISIBLE) {
					filter.setNameFilter(searchFilter.getText().toString());
				}
				settings.setPoiFilterForMap(filter.getFilterId());
				settings.SHOW_POI_OVER_MAP.set(true);
				if (location != null) {
					settings.setMapLocationToShow(location.getLatitude(), location.getLongitude(), 15);
				}
				MapActivity.launchMapActivityMoveToTop(SearchPOIActivity.this);
				return true;

			}
		});
		return true;
	}

	public Toolbar getClearToolbar(boolean visible) {
		final Toolbar tb = (Toolbar) findViewById(R.id.bottomControls);
		tb.setTitle(null);
		tb.getMenu().clear();
		tb.setVisibility(visible? View.VISIBLE : View.GONE);
		return tb;
	}
	
	private boolean searchMore() {
		String query = searchFilter.getText().toString().trim();
		if (query.length() < 2 && (isNameFinderFilter() || isSearchByNameFilter())) {
			AccessibleToast.makeText(SearchPOIActivity.this, R.string.poi_namefinder_query_empty, Toast.LENGTH_LONG).show();
			return true;
		}
		if (isNameFinderFilter() && !Algorithms.objectEquals(((NameFinderPoiFilter) filter).getQuery(), query)) {
			filter.clearPreviousZoom();
			((NameFinderPoiFilter) filter).setQuery(query);
			runNewSearchQuery(SearchAmenityRequest.buildRequest(location, SearchAmenityRequest.NEW_SEARCH_INIT));
		} else if (isSearchByNameFilter() && !Algorithms.objectEquals(((SearchByNameFilter) filter).getQuery(), query)) {
			showFilterItem.setVisible(false);
			filter.clearPreviousZoom();
			showPoiCategoriesByNameFilter(query, location);
			((SearchByNameFilter) filter).setQuery(query);
			runNewSearchQuery(SearchAmenityRequest.buildRequest(location, SearchAmenityRequest.NEW_SEARCH_INIT));
		} else {
			runNewSearchQuery(SearchAmenityRequest.buildRequest(location, SearchAmenityRequest.SEARCH_FURTHER));
		}
		return true;
	}

	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.searchpoi);
		
		getSupportActionBar().setTitle(R.string.searchpoi_activity);
		getSupportActionBar().setIcon(R.drawable.tab_search_poi_icon);
		setSupportProgressBarIndeterminateVisibility(false);
		
		app = (OsmandApplication)getApplication();
		
		uiHandler = new Handler();
		searchFilter = (EditText) findViewById(R.id.SearchFilter);
		searchFilterLayout = findViewById(R.id.SearchFilterLayout);

		settings = ((OsmandApplication) getApplication()).getSettings();
		
		searchFilter.addTextChangedListener(new TextWatcher(){
			@Override
			public void afterTextChanged(Editable s) {
				if(!isNameFinderFilter() && !isSearchByNameFilter()){
					amenityAdapter.getFilter().filter(s);
				} else {
					if(searchPOILevel != null)  {
						searchPOILevel.setEnabled(true);
						searchPOILevel.setTitle(R.string.search_button);
					}
					searchFooterButton.setEnabled(true);
					searchFooterButton.setText(R.string.search_button);
					// Cancel current search request here?
				}
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
		});
		searchFilter.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				}
			}
		});
		addFooterView();
		amenityAdapter = new AmenityAdapter(new ArrayList<Amenity>());
		setListAdapter(amenityAdapter);

		boolean light = getMyApplication().getSettings().isLightContent();
		Drawable arrowImage = getResources().getDrawable(R.drawable.ic_destination_arrow_white);
		if (light) {
			arrowImage.setColorFilter(getResources().getColor(R.color.color_distance), PorterDuff.Mode.MULTIPLY);
		} else {
			arrowImage.setColorFilter(getResources().getColor(R.color.color_distance), PorterDuff.Mode.MULTIPLY);
		}
	}

	private void addFooterView() {
		final FrameLayout ll = new FrameLayout(this);
		android.widget.FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.gravity = Gravity.CENTER_HORIZONTAL;
		searchFooterButton = new Button(this);
		searchFooterButton.setText(R.string.search_POI_level_btn);
		searchFooterButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				searchMore();
			}
		});
		searchFooterButton.setLayoutParams(lp);
		ll.addView(searchFooterButton);
		
		getListView().addFooterView(ll);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Bundle bundle = this.getIntent().getExtras();
		if(bundle.containsKey(SEARCH_LAT) && bundle.containsKey(SEARCH_LON)){
			location = new net.osmand.Location("internal"); //$NON-NLS-1$
			location.setLatitude(bundle.getDouble(SEARCH_LAT));
			location.setLongitude(bundle.getDouble(SEARCH_LON));
			searchNearBy = false;
		} else {
			location = null;
			searchNearBy = true;
		}
		
		
		String filterId = bundle.getString(AMENITY_FILTER);
		PoiLegacyFilter filter = app.getPoiFilters().getFilterById(filterId);
		if (filter != this.filter) {
			this.filter = filter;
			if (filter != null) {
				filter.clearPreviousZoom();
			} else {
				amenityAdapter.setNewModel(Collections.<Amenity> emptyList(), "");
			}
			// run query again
			clearSearchQuery();
		}
		if(filter != null) {
			filter.clearNameFilter();
		}
		
	
		updateSubtitle();
		updateSearchPoiTextButton(false);
		updateShowFilterItem();
		if (filter != null) {
			if (searchNearBy) {
				app.getLocationProvider().addLocationListener(this);
				location = app.getLocationProvider().getLastKnownLocation();
				app.getLocationProvider().resumeAllUpdates();
			}
			updateLocation(location);
		}
		if(isNameFinderFilter()){
			searchFilterLayout.setVisibility(View.VISIBLE);
		} else if(isSearchByNameFilter() ){
			searchFilterLayout.setVisibility(View.VISIBLE);
		}
		//Freeze the direction arrows (reference is constant north) when Accessibility mode = ON, so screen can be read aloud without continuous updates
		if(!app.accessibilityEnabled()) {
			app.getLocationProvider().addCompassListener(this);
			app.getLocationProvider().registerOrUnregisterCompassListener(true);
		}
		searchFilter.requestFocus();
	}


	private void updateShowFilterItem() {
		if(showFilterItem != null) {
			showFilterItem.setVisible(filter != null);
		}
	}

	private void updateSubtitle() {
		if(filter != null) {
			getSupportActionBar().setSubtitle(filter.getName() + " " + filter.getSearchArea());
		}
	}
	
	private void showPoiCategoriesByNameFilter(String query, net.osmand.Location loc){
		OsmandApplication app = (OsmandApplication) getApplication();
		if(loc != null){
			Map<PoiCategory, List<String>> map = app.getResourceManager().searchAmenityCategoriesByName(query, loc.getLatitude(), loc.getLongitude());
			if(!map.isEmpty()){
				PoiLegacyFilter filter = ((OsmandApplication)getApplication()).getPoiFilters().getFilterById(PoiLegacyFilter.CUSTOM_FILTER_ID);
				if(filter != null){
					showFilterItem.setVisible(true);
					filter.setMapToAccept(map);
				}
				
				String s = typesToString(map);
				AccessibleToast.makeText(this, getString(R.string.poi_query_by_name_matches_categories) + s, Toast.LENGTH_LONG).show();
			}
		}
	}
	
	private String typesToString(Map<PoiCategory, List<String>> map) {
		StringBuilder b = new StringBuilder();
		int count = 0;
		Iterator<Entry<PoiCategory, List<String>>> iterator = map.entrySet().iterator();
		while(iterator.hasNext() && count < 4){
			Entry<PoiCategory, List<String>> e = iterator.next();
			b.append("\n").append(e.getKey().getTranslation()).append(" - ");
			if(e.getValue() == null){
				b.append("...");
			} else {
				for(int j=0; j<e.getValue().size() && j < 3; j++){
					if(j > 0){
						b.append(", ");
					}
					b.append(e.getValue().get(j));
				}
			}
		}
		if(iterator.hasNext()){
			b.append("\n...");
		}
		return b.toString();
	}

	private void updateSearchPoiTextButton(boolean taskAlreadyFinished) {
		boolean enabled = false;
		int title = R.string.search_POI_level_btn;

		if (location == null) {
			title = R.string.search_poi_location;
			enabled = false;
		} else if (filter != null && !isNameFinderFilter() && !isSearchByNameFilter()) {
			title = R.string.search_POI_level_btn;
			enabled = (taskAlreadyFinished || currentSearchTask.getStatus() != Status.RUNNING) && filter.isSearchFurtherAvailable();
		} else if (filter != null) {
			// TODO: for search-by-name case, as long as filter text field is empty, we could disable the search button (with title search_button) until at least 2 characters are typed
			//title = R.string.search_button;
			// The following is needed as it indicates that search radius can be extended in search-by-name case
			title = R.string.search_POI_level_btn;
			enabled = (taskAlreadyFinished || currentSearchTask.getStatus() != Status.RUNNING) && filter.isSearchFurtherAvailable();
		}
		if (searchPOILevel != null) {
			searchPOILevel.setEnabled(enabled);
			searchPOILevel.setTitle(title);
		}
		//if(ResourcesCompat.getResources_getBoolean(this, R.bool.abs__split_action_bar_is_narrow)) {
		if(true) {
			searchFooterButton.setVisibility(View.GONE);
		} else {
			searchFooterButton.setVisibility(View.VISIBLE);
			searchFooterButton.setEnabled(enabled);
			searchFooterButton.setText(title);
		}
	}
	
	
	private net.osmand.Location getSearchedLocation(){
		return currentSearchTask.getSearchedLocation();
	}
	
	private synchronized void runNewSearchQuery(SearchAmenityRequest request){
		if(currentSearchTask.getStatus() == Status.FINISHED ||
				currentSearchTask.getSearchedLocation() == null){
			currentSearchTask = new SearchAmenityTask(request);
			currentSearchTask.execute();
		}
	}
	
	private synchronized void clearSearchQuery(){
		if(currentSearchTask.getStatus() == Status.FINISHED ||
				currentSearchTask.getSearchedLocation() == null){
			currentSearchTask = new SearchAmenityTask(null);
		}
	}
	
	
	public boolean isNameFinderFilter(){
		return filter instanceof NameFinderPoiFilter; 
	}
	
	public boolean isSearchByNameFilter(){
		return filter != null && PoiLegacyFilter.BY_NAME_FILTER_ID.equals(filter.getFilterId()); 
	}
	

	@Override
	public void updateLocation(net.osmand.Location location) {
		boolean handled = false;
		if (location != null && filter != null) {
			net.osmand.Location searchedLocation = getSearchedLocation();
			if (searchedLocation == null) {
  				searchedLocation = location;
				if (!isNameFinderFilter() && !isSearchByNameFilter()) {
					runNewSearchQuery(SearchAmenityRequest.buildRequest(location, SearchAmenityRequest.NEW_SEARCH_INIT));
				}
				handled = true;
			} else if (location.distanceTo(searchedLocation) > MIN_DISTANCE_TO_RESEARCH) {
				searchedLocation = location;
				runNewSearchQuery(SearchAmenityRequest.buildRequest(location, SearchAmenityRequest.SEARCH_AGAIN));
				handled = true;
			} else if (location.distanceTo(searchedLocation) > MIN_DISTANCE_TO_REFRESH){
				handled = true;
			}
		} else {
			if(location != null){
				handled = true;
			}
		}
		if (handled) {
			this.location = location;
			updateSearchPoiTextButton(false);
			// Get the top position from the first visible element
			int idx = getListView().getFirstVisiblePosition();
			View vfirst = getListView().getChildAt(0);
			int pos = 0;
			if (vfirst != null)
				pos = vfirst.getTop();
			amenityAdapter.notifyDataSetInvalidated();
			// Restore the position
			getListView().setSelectionFromTop(idx, pos);
		}	
		
	}

	@Override
	public void updateCompassValue(float value) {
		//99 in next line used to one-time initalize arrows (with reference vs. fixed-north direction) on non-compass devices
		float lastHeading = heading != null ? heading : 99;
		heading = value;
		if (heading != null && Math.abs(MapUtils.degreesDiff(lastHeading, heading)) > 5) {
			amenityAdapter.notifyDataSetChanged();
		} else {
			heading = lastHeading;
		}
		//Comment out and use lastHeading above to see if this fixes issues seen on some devices
		//if(!uiHandler.hasMessages(COMPASS_REFRESH_MSG_ID)){
		//	Message msg = Message.obtain(uiHandler, new Runnable(){
		//		@Override
		//		public void run() {
		//			amenityAdapter.notifyDataSetChanged();
		//		}
		//	});
		//	msg.what = COMPASS_REFRESH_MSG_ID;
		//	uiHandler.sendMessageDelayed(msg, 100);
		//}
	}
	
	
	@Override
	protected void onPause() {
		super.onPause();
		if (searchNearBy) {
			app.getLocationProvider().pauseAllUpdates();
			if(!app.accessibilityEnabled()) {
				app.getLocationProvider().removeCompassListener(this);
			}
			app.getLocationProvider().removeLocationListener(this);
		}
	}
	
	

	@Override
	public void onItemClick(AdapterView<?> parent,final View view, int position, long id) {
		final Amenity amenity = ((AmenityAdapter) getListAdapter()).getItem(position);
		String poiSimpleFormat = OsmAndFormatter.getPoiSimpleFormat(amenity, getMyApplication(), settings.usingEnglishNames());
		PointDescription name = new PointDescription(PointDescription.POINT_TYPE_POI, poiSimpleFormat);
		int z = Math.max(16, settings.getLastKnownMapZoom());
		final PopupMenu optionsMenu = new PopupMenu(this, view);
		DirectionsDialogs.createDirectionsActionsPopUpMenu(optionsMenu, amenity.getLocation(), amenity, name, z, this, true);
		final String d = OsmAndFormatter.getAmenityDescriptionContent(getMyApplication(), amenity, false);
		if(d.toString().trim().length() > 0) {
			MenuItem item = optionsMenu.getMenu().add(R.string.poi_context_menu_showdescription).
					setIcon(getMyApplication().getSettings().isLightContent() ?
							R.drawable.ic_action_note_light : R.drawable.ic_action_note_dark);
			item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					// Build text(amenity)

					// Find and format links
					SpannableString spannable = new SpannableString(d);
					Linkify.addLinks(spannable, Linkify.ALL);

					// Create dialog
					Builder bs = new AlertDialog.Builder(view.getContext());
					bs.setTitle(OsmAndFormatter.getPoiSimpleFormat(amenity, getMyApplication(),
							settings.usingEnglishNames()));
					bs.setMessage(spannable);
					AlertDialog dialog = bs.show();

					// Make links clickable
					TextView textView = (TextView) dialog.findViewById(android.R.id.message);
					textView.setMovementMethod(LinkMovementMethod.getInstance());
					textView.setLinksClickable(true);
					return true;
				}
			});
		}
		if (((OsmandApplication)getApplication()).accessibilityEnabled()) {
			MenuItem item = optionsMenu.getMenu().add(R.string.show_details);
			item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					showPOIDetails(amenity, settings.usingEnglishNames());
					return true;
				}
			});
		}
		optionsMenu.show();
	}


	static class SearchAmenityRequest {
		private static final int SEARCH_AGAIN = 1;
		private static final int NEW_SEARCH_INIT = 2;
		private static final int SEARCH_FURTHER = 3;
		private int type;
		private net.osmand.Location location;
		
		public static SearchAmenityRequest buildRequest(net.osmand.Location l, int type){
			SearchAmenityRequest req = new SearchAmenityRequest();
			req.type = type;
			req.location = l;
			return req;
			
		}
	}
	
	class SearchAmenityTask extends AsyncTask<Void, Amenity, List<Amenity>> implements ResultMatcher<Amenity> {
		
		private SearchAmenityRequest request;
		private TLongHashSet existingObjects = null;
		private TLongHashSet updateExisting;
		
		public SearchAmenityTask(SearchAmenityRequest request){
			this.request = request;
			
		}
		
		net.osmand.Location getSearchedLocation(){
			return request != null ? request.location : null; 
		}

		@Override
		protected void onPreExecute() {
			//getSherlock().setProgressBarIndeterminateVisibility(true);
			if(searchPOILevel != null) {
				searchPOILevel.setEnabled(false);
			}
			searchFooterButton.setEnabled(false);
			existingObjects = new TLongHashSet();
			updateExisting = new TLongHashSet();
			if(request.type == SearchAmenityRequest.NEW_SEARCH_INIT){
				amenityAdapter.clear();
			} else if (request.type == SearchAmenityRequest.SEARCH_FURTHER) {
				List<Amenity> list = amenityAdapter.getOriginalAmenityList();
				for (Amenity a : list) {
					updateExisting.add(getAmenityId(a));
				}
			}
		}
		private long getAmenityId(Amenity a){
			return (a.getId() << 8) + a.getType().ordinal();
		}
		
		@Override
		protected void onPostExecute(List<Amenity> result) {
			//getSherlock().setProgressBarIndeterminateVisibility(false);
			updateSearchPoiTextButton(true);
			if (isNameFinderFilter()) {
				if (!Algorithms.isEmpty(((NameFinderPoiFilter) filter).getLastError())) {
					AccessibleToast.makeText(SearchPOIActivity.this, ((NameFinderPoiFilter) filter).getLastError(), Toast.LENGTH_LONG).show();
				}
				amenityAdapter.setNewModel(result, "");
				showOnMapItem.setEnabled(amenityAdapter.getCount() > 0);
			} else if (isSearchByNameFilter()) {
				showOnMapItem.setEnabled(amenityAdapter.getCount() > 0);
				amenityAdapter.setNewModel(result, "");
			} else {
				amenityAdapter.setNewModel(result, searchFilter.getText().toString());
			}
			updateSubtitle();
		}
		
		@Override
		protected void onProgressUpdate(Amenity... values) {
			for(Amenity a : values){
				amenityAdapter.add(a);
			}
		}


		@Override
		protected List<Amenity> doInBackground(Void... params) {
			if (request.location != null) {
				if (request.type == SearchAmenityRequest.NEW_SEARCH_INIT) {
					return filter.initializeNewSearch(request.location.getLatitude(), request.location.getLongitude(), -1, this);
				} else if (request.type == SearchAmenityRequest.SEARCH_FURTHER) {
					return filter.searchFurther(request.location.getLatitude(), request.location.getLongitude(), this);
				} else if (request.type == SearchAmenityRequest.SEARCH_AGAIN) {
					return filter.searchAgain(request.location.getLatitude(), request.location.getLongitude());
				}
			}
			return Collections.emptyList();
		}

		@Override
		public boolean publish(Amenity object) {
			long id = getAmenityId(object);
			if (existingObjects != null && !existingObjects.contains(id)) {
				existingObjects.add(id);
				if (request.type == SearchAmenityRequest.NEW_SEARCH_INIT) {
					publishProgress(object);
				} else if (request.type == SearchAmenityRequest.SEARCH_FURTHER) {
					if(!updateExisting.contains(id)){
						publishProgress(object);
					}
				}
				return true;
			}
			return false;
		}
		
	}
	
	
	class AmenityAdapter extends ArrayAdapter<Amenity> {
		private AmenityFilter listFilter;
		private List<Amenity> originalAmenityList;
		AmenityAdapter(List<Amenity> list) {
			super(SearchPOIActivity.this, R.layout.searchpoi_list, list);
			originalAmenityList = new ArrayList<Amenity>(list);
			this.setNotifyOnChange(false);
		}
		

		public List<Amenity> getOriginalAmenityList() {
			return originalAmenityList;
		}


		public void setNewModel(List<Amenity> amenityList, String filter) {
			setNotifyOnChange(false);
			originalAmenityList = new ArrayList<Amenity>(amenityList);
			clear();
			for (Amenity obj : amenityList) {
				add(obj);
			}
			getFilter().filter(filter);
			setNotifyOnChange(true);
			this.notifyDataSetChanged();
			
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.searchpoi_list, parent, false);
			}
			float[] mes = null;
			TextView label = (TextView) row.findViewById(R.id.poi_label);
			TextView distanceText = (TextView) row.findViewById(R.id.distance);
			ImageView direction = (ImageView) row.findViewById(R.id.poi_direction);
			ImageView icon = (ImageView) row.findViewById(R.id.poi_icon);
			Amenity amenity = getItem(position);
			net.osmand.Location loc = location;
			if(loc != null){
				mes = new float[2];
				LatLon l = amenity.getLocation();
				net.osmand.Location.distanceBetween(l.getLatitude(), l.getLongitude(), loc.getLatitude(), loc.getLongitude(), mes);
			}
			int opened = -1;
			if (amenity.getOpeningHours() != null) {
				OpeningHours rs = OpeningHoursParser.parseOpenedHours(amenity.getOpeningHours());
				if (rs != null) {
					Calendar inst = Calendar.getInstance();
					inst.setTimeInMillis(System.currentTimeMillis());
					boolean work = false;
					work = rs.isOpenedForTime(inst);
					if (work) {
						opened = 0;
					} else {
						opened = 1;
					}
				}
			}
			if(loc != null){
				DirectionDrawable draw = new DirectionDrawable(SearchPOIActivity.this, width, height, 
						R.drawable.ic_destination_arrow_white, R.color.color_distance);
				Float h = heading;
				float a = h != null ? h : 0;

				//Hardy: getRotation() is the correction if device's screen orientation != the default display's standard orientation
				int screenOrientation = 0;
				screenOrientation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
				switch (screenOrientation)
				{
				case ORIENTATION_0:   // Device default (normally portrait)
					screenOrientation = 0;
					break;
				case ORIENTATION_90:  // Landscape right
					screenOrientation = 90;
					break;
				case ORIENTATION_270: // Landscape left
					screenOrientation = 270;
					break;
				case ORIENTATION_180: // Upside down
					screenOrientation = 180;
					break;
				}

				//Looks like screenOrientation correction must not be applied for devices without compass?
				Sensor compass  = ((SensorManager) getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
				if (compass == null) {
					screenOrientation = 0;
				}

				draw.setAngle(mes[1] - a + 180 + screenOrientation);

				draw.setOpenedColor(opened);
				direction.setImageDrawable(draw);
			} else {
				if(opened == -1){
					direction.setImageResource(R.drawable.poi);
				} else if(opened == 0){
					direction.setImageResource(R.drawable.opened_poi);
				} else {
					direction.setImageResource(R.drawable.closed_poi);
				}
			}
			PoiType st = amenity.getType().getPoiTypeByKeyName(amenity.getSubType());
			if (st != null) {
				if (RenderingIcons.containsBigIcon(st.getKeyName())) {
					icon.setImageResource(RenderingIcons.getBigIconResourceId(st.getKeyName()));
				} else if (RenderingIcons.containsBigIcon(st.getOsmTag() + "_" + st.getOsmValue())) {
					icon.setImageResource(RenderingIcons.getBigIconResourceId(st.getOsmTag() + "_" + st.getOsmValue()));
				} else if (RenderingIcons.containsBigIcon(st.getOsmTag() + "_" + st.getOsmValue())) {
					icon.setImageResource(RenderingIcons.getBigIconResourceId(st.getOsmValue()));
				} else {
					icon.setImageDrawable(null);
				}
			} else {
				icon.setImageDrawable(null);
			}

			String distance = "  ";
			if(mes != null){
				distance = " " + OsmAndFormatter.getFormattedDistance((int) mes[0], getMyApplication()) + "  "; //$NON-NLS-1$
			}
			String poiType = OsmAndFormatter.getPoiStringWithoutType(amenity, settings.usingEnglishNames());
			label.setText(poiType);
			distanceText.setText(distance);
			return (row);
		}
		
		@Override
		public Filter getFilter() {
			if (listFilter == null) {
				listFilter = new AmenityFilter();
			}
			return listFilter;
		}
		
		private final class AmenityFilter extends Filter {
			
			@Override
			protected FilterResults performFiltering(CharSequence constraint) {
				FilterResults results = new FilterResults();
				List<Amenity> listToFilter = originalAmenityList;
				if (constraint == null || constraint.length() == 0) {
					results.values = listToFilter;
					results.count = listToFilter.size();
				} else {
					String lowerCase = constraint.toString()
							.toLowerCase();
					List<Amenity> filter = new ArrayList<Amenity>();
					for (Amenity item : listToFilter) {
						String lower = OsmAndFormatter.getPoiStringWithoutType(item, settings.usingEnglishNames()).toLowerCase();
						if(lower.indexOf(lowerCase) != -1){
							filter.add(item);
						}
					}
					results.values = filter;
					results.count = filter.size();
				}
				return results;
			}

			@SuppressWarnings("unchecked")
			@Override
			protected void publishResults(CharSequence constraint, FilterResults results) {
				clear();
				for (Amenity item : (Collection<Amenity>) results.values) {
					add(item);
				}
			}
		}
	}

	private void showPOIDetails(final Amenity amenity, boolean en) {
		AlertDialog.Builder b = new AlertDialog.Builder(SearchPOIActivity.this);
		b.setTitle(OsmAndFormatter.getPoiSimpleFormat(amenity, getMyApplication(), en));
		b.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		List<String> attributes = new ArrayList<String>();
		NavigationInfo navigationInfo = app.getLocationProvider().getNavigationInfo();
		String direction = navigationInfo.getDirectionString(amenity.getLocation(), heading);
		if (direction != null)
			attributes.add(direction);
		String[] as = OsmAndFormatter.getAmenityDescriptionContent(getMyApplication(), amenity, false).split("\n");
		for(String s: as) {
			attributes.add(s.replace(':', ' '));
		}
		attributes.add(getString(R.string.navigate_point_latitude) + " " + Double.toString(amenity.getLocation().getLatitude()));
		attributes.add(getString(R.string.navigate_point_longitude) + " " + Double.toString(amenity.getLocation().getLongitude()));
		b.setItems(attributes.toArray(new String[attributes.size()]),
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
				}
			});
		b.show();
	}

}
