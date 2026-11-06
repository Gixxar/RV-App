package com.poulton.rvapp;

import android.app.*;
import android.os.*;
import android.content.*;
import android.location.*;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit, ordered route planning. Saved plans never create travelled routes or visits. */
public final class RoutePlannerActivity extends Activity {
    private final ArrayList<JSONObject> points=new ArrayList<>();
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private EditText name,date,notes;
    private Spinner trip;
    private final ArrayList<String> tripIds=new ArrayList<>(),tripNames=new ArrayList<>();
    private LinearLayout stopsView;
    private TextView estimate;
    private Button calculate,save;
    private WebView map;
    private JSONObject computed;
    private JSONObject activeProfile=new JSONObject();
    private CheckBox useRv;
    private TextView profileSummary,routingNotice;
    private long editId;
    private int mapIndex=-1,generation;
    private boolean dirty,busy,mapReady;
    private final TextWatcher changes=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){dirty=true;}public void afterTextChanged(Editable e){}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);editId=getIntent().getLongExtra("id",0);JSONObject saved=new JSONObject();
        if(editId!=0){try(LogStore store=new LogStore(this)){for(LogStore.Entry e:store.list(Module.PLANS))if(e.id==editId)saved=e.data;}if(saved.length()==0){Toast.makeText(this,"Route plan no longer available",Toast.LENGTH_LONG).show();finish();return;}}
        try{
            String encoded=state==null?saved.optString("Route stops","[{},{}]"):state.getString("stops","[{},{}]");JSONArray stops=new JSONArray(encoded);
            for(int i=0;i<Math.min(stops.length(),10);i++)points.add(stops.getJSONObject(i));while(points.size()<2)points.add(new JSONObject());
            if(saved.has("Route points"))computed=new JSONObject(saved.toString());
            Object retained=getLastNonConfigurationInstance();if(retained instanceof JSONObject)computed=(JSONObject)retained;
            if(computed!=null&&!computed.optString("Route stops").equals(encodedStops().toString()))computed=null;
        }catch(Exception e){points.clear();points.add(new JSONObject());points.add(new JSONObject());computed=null;}
        if(state!=null)mapIndex=state.getInt("mapIndex",-1);
        try(LogStore store=new LogStore(this)){activeProfile=VehicleProfile.current(store);}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(AdventureStyle.PAPER);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(body);
        label(body,editId==0?"Plan a route":"Edit route plan",27,AdventureStyle.INK);
        routingNotice=label(body,RouteService.NOTICE,14,AdventureStyle.MUTED);
        add(body,"My RV dimensions",()->startActivityForResult(new Intent(this,VehicleProfileActivity.class),70));
        profileSummary=label(body,VehicleProfile.summary(activeProfile),14,AdventureStyle.INK);
        useRv=new CheckBox(this);useRv.setText(R.string.use_rv_dimensions);useRv.setMinHeight(dp(48));useRv.setTextColor(AdventureStyle.INK);useRv.setChecked(state!=null?state.getBoolean("useRv"):editId==0?VehicleProfile.complete(activeProfile):RvRouting.TYPE.equals(saved.optString("Route type")));body.addView(useRv);
        useRv.setOnCheckedChangeListener((v,checked)->{invalidateRoute();updateProfileNotice();});
        if(computed!=null&&!compatible(computed))computed=null;updateProfileNotice();
        name=field(body,"Plan name",state==null?saved.optString("Name"):state.getString("name",""),300);
        date=field(body,"Travel date (optional)",state==null?saved.optString("Date"):state.getString("date",""),10);date.setHint("YYYY-MM-DD");
        add(body,"Pick date",()->{java.time.LocalDate today;try{today=java.time.LocalDate.parse(date.getText().toString());}catch(Exception e){today=java.time.LocalDate.now();}new DatePickerDialog(this,(d,y,m,day)->date.setText(java.time.LocalDate.of(y,m+1,day).toString()),today.getYear(),today.getMonthValue()-1,today.getDayOfMonth()).show();});
        label(body,"Link to a trip",13,AdventureStyle.MUTED);trip=new Spinner(this);ArrayList<String> names=new ArrayList<>();names.add("Unlinked");
        tripIds.add("");tripNames.add("");String selectedId=state==null?saved.optString("Trip ID",getIntent().getStringExtra("trip")==null?"":getIntent().getStringExtra("trip")):state.getString("tripId","");
        try(LogStore store=new LogStore(this)){for(LogStore.Entry e:store.list(Module.TRIPS)){String key=TripWorkflow.identify(e.data,"Trip ID");store.save(Module.TRIPS,e.id,e.data);names.add(e.data.optString("Name")+" · "+e.data.optString("Date"));tripIds.add(key);tripNames.add(e.data.optString("Name"));}}
        if(!selectedId.isEmpty()&&!tripIds.contains(selectedId)){names.add("Previously linked trip (deleted)");tripIds.add(selectedId);tripNames.add(saved.optString("Trip"));}
        if(selectedId.isEmpty()&&!saved.optString("Trip").isEmpty()){names.add(saved.optString("Trip")+" (legacy label; choose a trip to link)");tripIds.add("");tripNames.add(saved.optString("Trip"));}
        trip.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));trip.setSelection(selectedId.isEmpty()&&!saved.optString("Trip").isEmpty()?names.size()-1:Math.max(0,tripIds.indexOf(selectedId)));body.addView(trip);
        trip.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){boolean first=true;public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!first)dirty=true;first=false;}});
        label(body,"Stops in travel order",20,AdventureStyle.INK);stopsView=new LinearLayout(this);stopsView.setOrientation(LinearLayout.VERTICAL);body.addView(stopsView);renderStops();
        add(body,"Add intermediate stop",()->{if(points.size()>=10){toast("Maximum 10 stops including start and destination");return;}points.add(points.size()-1,new JSONObject());invalidateRoute();renderStops();});
        label(body,"Calculate sends coordinates to OSRM for car routes, or coordinates and RV measurements to Valhalla for RV routes. Internet required. Estimates exclude stop time and may be inaccurate.",13,AdventureStyle.MUTED);
        calculate=add(body,"Calculate road route",this::calculateRoute);
        estimate=label(body,"Choose your start, destination and any stops, then calculate.",16,AdventureStyle.INK);
        map=new WebView(this);body.addView(map,new LinearLayout.LayoutParams(-1,dp(260)));configureMap();
        label(body,"Routing: OSRM / Valhalla · Map data: OpenStreetMap contributors",12,AdventureStyle.MUTED);add(body,"Map data / fix the map",()->open(Uri.parse("https://www.openstreetmap.org/")));
        add(body,"Navigate a leg in Maps",this::navigateLeg);
        notes=field(body,"Notes",state==null?saved.optString("Notes"):state.getString("notes",""),16000);notes.setSingleLine(false);notes.setMinLines(3);
        if(editId!=0)add(body,"Delete route plan",()->new AlertDialog.Builder(this).setMessage("Delete this saved route plan?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try(LogStore store=new LogStore(this)){store.delete(editId);}dirty=false;setResult(RESULT_OK);finish();}).show());
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(16),dp(8),dp(16),dp(8));actions.setBackgroundColor(AdventureStyle.CARD);root.addView(actions);
        Button back=add(actions,"Cancel",this::onBackPressed);save=add(actions,"Save plan",this::savePlan);LinearLayout.LayoutParams backLp=new LinearLayout.LayoutParams(0,-2,1);backLp.setMargins(0,0,dp(10),0);back.setLayoutParams(backLp);save.setLayoutParams(new LinearLayout.LayoutParams(0,-2,2));save.setTextColor(AdventureStyle.CARD);save.setBackground(AdventureStyle.vibrant(this,Module.PLANS));
        updatePreview();dirty=state!=null&&state.getBoolean("dirty");
    }
    private EditText field(LinearLayout parent,String title,String value,int limit){
        TextView caption=label(parent,title,13,AdventureStyle.MUTED);EditText field=new EditText(this);field.setId(View.generateViewId());caption.setLabelFor(field.getId());field.setTextSize(16);field.setSingleLine(true);field.setTextColor(AdventureStyle.TEXT);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});field.setBackground(AdventureStyle.input(this));field.setPadding(dp(14),dp(12),dp(14),dp(12));field.setMinHeight(dp(52));field.setText(value);parent.addView(field,new LinearLayout.LayoutParams(-1,-2));field.addTextChangedListener(changes);return field;
    }
    private TextView label(LinearLayout parent,String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(10),0,dp(8));if(size>=20)t.setTypeface(AdventureStyle.DISPLAY);parent.addView(t);return t;}
    private Button add(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setTextColor(AdventureStyle.accent(Module.PLANS));b.setBackground(AdventureStyle.pressable(this,AdventureStyle.tint(Module.PLANS),AdventureStyle.BORDER));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(2),dp(4),dp(2),dp(4));parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density);}
    private JSONArray encodedStops(){return new JSONArray(points);}
    private void renderStops(){
        stopsView.removeAllViews();
        for(int index=0;index<points.size();index++){
            final int i=index;JSONObject p=points.get(i);String role=i==0?"Start":i==points.size()-1?"Destination":"Stop "+i;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(4),dp(10),dp(8));card.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.BORDER));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));stopsView.addView(card,lp);
            add(card,role+" · "+p.optString("name","Choose a place"),()->chooseStop(p));
            if(p.has("lat"))label(card,String.format(Locale.UK,"%.5f, %.5f",p.optDouble("lat"),p.optDouble("lon")),12,AdventureStyle.MUTED);
            LinearLayout row=new LinearLayout(this);card.addView(row);
            Button map=add(row,"Map",()->{mapIndex=i;startActivityForResult(new Intent(this,TravelMapActivity.class).putExtra("pick_stop",true),60);});
            Button up=add(row,"Up",()->move(i,-1)),down=add(row,"Down",()->move(i,1)),remove=add(row,"Remove",()->{points.remove(i);invalidateRoute();renderStops();});up.setEnabled(i>0);down.setEnabled(i<points.size()-1);remove.setEnabled(points.size()>2);
            for(Button b:new Button[]{map,up,down,remove}){b.setTextSize(12);b.setAlpha(b.isEnabled()?1f:.4f);LinearLayout.LayoutParams btn=new LinearLayout.LayoutParams(0,-2,1);btn.setMargins(dp(2),0,dp(2),0);b.setLayoutParams(btn);}
        }
    }
    private void move(int index,int offset){Collections.swap(points,index,index+offset);invalidateRoute();renderStops();}
    private void chooseStop(JSONObject target){
        new AlertDialog.Builder(this).setTitle("Choose a route stop").setItems(new String[]{"Search address or postcode","Enter coordinates","Saved visited place","Saved campsite"},(d,index)->{
            if(index==0)searchStop(target,"");else if(index==1)coordinates(target);else savedStop(target,index==2?Module.PLACES:Module.CAMPSITES);
        }).setNegativeButton("Cancel",null).show();
    }
    private void coordinates(JSONObject target){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),0,dp(20),0);EditText title=field(form,"Place name",target.optString("name"),300),lat=field(form,"Latitude",target.optString("lat"),30),lon=field(form,"Longitude",target.optString("lon"),30);
        lat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);lon.setInputType(lat.getInputType());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Enter a stop position").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Use stop",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{double a=Double.parseDouble(lat.getText().toString()),b=Double.parseDouble(lon.getText().toString());if(!GeoData.valid(a,b))throw new IllegalArgumentException();setStop(target,title.getText().toString().trim(),a,b);dialog.dismiss();}catch(Exception e){lat.setError("Enter valid decimal coordinates");}}));dialog.show();
    }
    private void savedStop(JSONObject target,Module module){
        List<LogStore.Entry> rows;try(LogStore store=new LogStore(this)){rows=store.list(module);}if(rows.isEmpty()){toast("No saved "+module.title.toLowerCase(Locale.ROOT)+" yet");return;}
        String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=rows.get(i).data.optString("Name")+(module==Module.CAMPSITES?" · search location":"");
        new AlertDialog.Builder(this).setTitle(module.title).setItems(labels,(d,index)->{JSONObject row=rows.get(index).data;try{if(module==Module.PLACES)setStop(target,row.optString("Name"),row.getDouble("Latitude"),row.getDouble("Longitude"));else searchStop(target,row.optString("Name")+" "+row.optString("Address"));}catch(Exception e){toast("Choose this stop on the map instead");}}).setNegativeButton("Cancel",null).show();
    }
    private void searchStop(JSONObject target,String suggested){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(suggested);input.setHint("Town, postcode or address");
        new AlertDialog.Builder(this).setTitle("Find a route stop").setMessage("Search uses your Android search provider. Choose the result carefully, then check its position on the map.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Search",(d,w)->{
            String query=input.getText().toString().trim();if(query.isEmpty())return;if(!Geocoder.isPresent()){toast("Search unavailable. Use coordinates or choose a position on the map.");return;}toast("Searching…");
            jobs.execute(()->{try{List<Address> found=new Geocoder(getApplicationContext(),Locale.getDefault()).getFromLocationName(query,5);runOnUiThread(()->{if(isFinishing()||isDestroyed()||!points.contains(target))return;if(found==null||found.isEmpty()){toast("No results. Try a postcode or choose a stop on the map.");return;}String[] labels=new String[found.size()];for(int i=0;i<labels.length;i++)labels[i]=found.get(i).getAddressLine(0)==null?query:found.get(i).getAddressLine(0);new AlertDialog.Builder(this).setTitle("Confirm stop location").setItems(labels,(dialog,index)->{Address a=found.get(index);setStop(target,labels[index],a.getLatitude(),a.getLongitude());}).setNegativeButton("Cancel",null).show();});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())toast("Search failed. Use coordinates or choose a stop on the map.");});}});
        }).show();
    }
    private void setStop(JSONObject target,String title,double lat,double lon){
        if(!GeoData.valid(lat,lon)||!points.contains(target))return;try{target.put("name",title.isEmpty()?"Map stop":title.substring(0,Math.min(title.length(),300))).put("lat",lat).put("lon",lon);invalidateRoute();renderStops();}catch(JSONException e){toast("Could not set this stop");}
    }
    private void invalidateRoute(){generation++;computed=null;dirty=true;updatePreview();}
    private void calculateRoute(){
        JSONArray requested;try{requested=RouteService.stops(encodedStops().toString());}catch(Exception e){toast("Choose valid, different positions for all stops first");return;}
        final boolean rvMode=useRv.isChecked();final JSONObject profile;try{profile=rvMode?VehicleProfile.normalized(activeProfile):new JSONObject();}catch(Exception e){toast("Save all four RV dimensions before calculating an RV route");return;}
        busy=true;calculate.setEnabled(false);save.setEnabled(false);estimate.setText(R.string.calculating_route);int version=++generation;
        jobs.execute(()->{try{JSONObject result=rvMode?RvRouting.calculate(requested,profile):RouteService.calculate(requested);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;calculate.setEnabled(true);if(generation==version)acceptRoute(result);else updatePreview();});}catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;calculate.setEnabled(true);updatePreview();if(generation==version)new AlertDialog.Builder(this).setTitle("Route could not be calculated").setMessage(e.getMessage()==null?"Check your connection and try again. Your stops are kept.":e.getMessage()).setPositiveButton("Close",null).show();});}});
    }
    void acceptRoute(JSONObject route){
        if(!route.optString("Route stops").equals(encodedStops().toString())||!compatible(route))return;
        try{GeoData.parse(route.getString("Route points"));Map<String,String> values=new HashMap<>();for(String key:Module.PLANS.fields)values.put(key,route.optString(key));RouteService.validatePlan(values);computed=route;dirty=true;updatePreview();}catch(Exception e){toast("Invalid route result. Try again.");}
    }
    private void updatePreview(){
        if(estimate==null)return;
        if(computed==null)estimate.setText(R.string.plan_needs_calculation);
        else estimate.setText(getString(RvRouting.TYPE.equals(computed.optString("Route type"))?R.string.rv_route_estimate:R.string.route_estimate,computed.optString("Distance miles"),computed.optString("Drive minutes")));
        if(save!=null){save.setEnabled(computed!=null&&!busy);save.setAlpha(save.isEnabled()?1f:.45f);}
        if(mapReady){try{JSONObject data=new JSONObject().put("stops",encodedStops());if(computed!=null)data.put("segments",GeoData.parse(computed.getString("Route points")));map.evaluateJavascript("window.showPlan("+data+")",null);}catch(Exception ignored){}}
    }
    private void savePlan(){
        if(computed==null||busy)return;
        try(LogStore check=new LogStore(this)){activeProfile=VehicleProfile.current(check);}if(!compatible(computed)){invalidateRoute();updateProfileNotice();toast("RV profile changed. Recalculate before saving.");return;}
        try{
            JSONObject plan=new JSONObject(computed.toString()).put("Name",name.getText().toString().trim()).put("Date",date.getText().toString().trim()).put("Trip",tripNames.get(trip.getSelectedItemPosition())).put("Notes",notes.getText().toString().trim()).put("Trip ID",tripIds.get(trip.getSelectedItemPosition()));
            Map<String,String> values=new HashMap<>();for(String field:Module.PLANS.fields)values.put(field,plan.optString(field));String error=Rules.validateRecord(Module.PLANS,values);if(error!=null){toast(error);return;}
            try(LogStore store=new LogStore(this)){store.save(Module.PLANS,editId,plan);}dirty=false;setResult(RESULT_OK);finish();
        }catch(Exception e){toast("Could not save this plan. Your draft is still open.");}
    }
    private void updateProfileNotice(){if(routingNotice!=null)routingNotice.setText(useRv.isChecked()?VehicleProfile.RV_NOTICE:RouteService.NOTICE);if(profileSummary!=null)profileSummary.setText(getString(R.string.rv_profile_summary,VehicleProfile.summary(activeProfile),VehicleProfile.complete(activeProfile)?VehicleProfile.axleNotice(activeProfile):""));}
    private boolean compatible(JSONObject route){if(useRv==null)return true;if(!useRv.isChecked())return RouteService.TYPE.equals(route.optString("Route type"));try{return RvRouting.TYPE.equals(route.optString("Route type"))&&VehicleProfile.same(activeProfile,new JSONObject(route.getString("RV profile")));}catch(Exception e){return false;}}
    private void navigateLeg(){
        if(computed!=null&&RvRouting.TYPE.equals(computed.optString("Route type"))){new AlertDialog.Builder(this).setTitle("Google Maps uses car routing").setMessage("Google Maps will calculate its own route. Your RV dimensions are not transferred, and it may choose roads excluded from this RV plan. Use RV-aware navigation and check road signs.").setNegativeButton("Cancel",null).setPositiveButton("Choose a car leg",(d,w)->chooseLeg()).show();return;}chooseLeg();
    }
    private void chooseLeg(){
        if(computed==null){toast("Calculate your route first");return;}
        JSONArray routeStops;try{routeStops=RouteService.stops(computed.getString("Route stops"));}catch(Exception e){return;}
        String[] legs=new String[routeStops.length()-1];for(int i=0;i<legs.length;i++)legs[i]=routeStops.optJSONObject(i).optString("name")+" → "+routeStops.optJSONObject(i+1).optString("name");
        new AlertDialog.Builder(this).setTitle("Navigate one leg").setItems(legs,(d,index)->{try{RouteService.UriParts leg=RouteService.leg(routeStops,index);open(new Uri.Builder().scheme("https").authority("www.google.com").path("/maps/dir/").appendQueryParameter("api","1").appendQueryParameter("origin",leg.origin).appendQueryParameter("destination",leg.destination).appendQueryParameter("travelmode","driving").build());}catch(Exception e){toast("Could not open this leg");}}).setNegativeButton("Cancel",null).show();
    }
    private void open(Uri uri){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){toast("No browser or maps app available");}}
    @android.annotation.SuppressLint("SetJavaScriptEnabled") private void configureMap(){
        WebSettings settings=map.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setGeolocationEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setUserAgentString(settings.getUserAgentString()+" PoultonsRVLogbook/1.7 (+https://github.com/Gixxar/RV-App)");
        map.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){Uri u=request.getUrl();if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())&&Arrays.asList("/map/planner.html","/map/planner.js","/map/leaflet.js","/map/leaflet.css").contains(u.getPath()))try{return new WebResourceResponse(u.getPath().endsWith(".js")?"application/javascript":u.getPath().endsWith(".css")?"text/css":"text/html","UTF-8",getAssets().open(u.getPath().substring(1)));}catch(IOException ignored){}if("https".equals(u.getScheme())&&"tile.openstreetmap.org".equals(u.getHost())&&!request.isForMainFrame())return null;return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri u=request.getUrl();if("https".equals(u.getScheme())&&"www.openstreetmap.org".equals(u.getHost()))open(u);return true;}
            @Override public void onPageFinished(WebView view,String url){if(url.equals("https://appassets.androidplatform.net/map/planner.html")){mapReady=true;updatePreview();}}
        });map.loadUrl("https://appassets.androidplatform.net/map/planner.html");
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==70){if(result==RESULT_OK){try(LogStore store=new LogStore(this)){activeProfile=VehicleProfile.current(store);}if(VehicleProfile.complete(activeProfile))useRv.setChecked(true);invalidateRoute();updateProfileNotice();}return;}if(request==60&&result==RESULT_OK&&data!=null&&mapIndex>=0&&mapIndex<points.size()){try{setStop(points.get(mapIndex),data.getStringExtra("Name")==null?"Map stop":data.getStringExtra("Name"),Double.parseDouble(data.getStringExtra("Latitude")),Double.parseDouble(data.getStringExtra("Longitude")));}catch(Exception e){toast("Choose this stop again");}}mapIndex=-1;}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(name==null)return;state.putBoolean("useRv",useRv.isChecked());state.putString("stops",encodedStops().toString());state.putString("name",name.getText().toString());state.putString("date",date.getText().toString());state.putString("trip",trip.getSelectedItemPosition()==0?"":trip.getSelectedItem().toString());state.putString("tripId",tripIds.get(trip.getSelectedItemPosition()));state.putString("notes",notes.getText().toString());state.putInt("mapIndex",mapIndex);state.putBoolean("dirty",dirty);}
    @Override public Object onRetainNonConfigurationInstance(){return computed;}
    @Override public void onBackPressed(){if(dirty)new AlertDialog.Builder(this).setMessage("Discard unsaved route plan changes?").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->finish()).show();else finish();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){jobs.shutdownNow();if(map!=null){if(map.getParent() instanceof ViewGroup)((ViewGroup)map.getParent()).removeView(map);map.destroy();map=null;}super.onDestroy();}
}
