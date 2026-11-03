package com.poulton.rvapp;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Insets;
import android.location.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Local map UI with separate travelled routes and route plans. */
public final class TravelMapActivity extends Activity {
    private WebView web;
    private JSONObject payload;
    private String filter="";
    private boolean pickingStop;
    private final ExecutorService search=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);pickingStop=getIntent().getBooleanExtra("pick_stop",false);if(state!=null)filter=state.getString("filter","");
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(AdventureStyle.PAPER);setContentView(page);
        page.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        TextView title=new TextView(this);title.setText(pickingStop?R.string.pick_route_stop:R.string.travel_map_title);title.setTypeface(AdventureStyle.DISPLAY);title.setTextSize(21);title.setTextColor(AdventureStyle.INK);title.setPadding(dp(12),dp(10),dp(12),dp(5));page.addView(title);
        LinearLayout top=new LinearLayout(this);page.addView(top);control(top,"Back",this::finish);control(top,"Fit all",()->js("fitAll()"));control(top,"Find place",this::findPlace);
        LinearLayout tools=new LinearLayout(this);page.addView(tools);
        if(pickingStop)control(tools,"Choose stop on map",()->js("pickStop()"));
        else{control(tools,"Add place",()->js("addPlace()"));control(tools,"Draw route",()->js("drawRoute()"));control(tools,"Import GPX",()->result("import",new Intent()));LinearLayout planning=new LinearLayout(this);page.addView(planning);control(planning,"Plan a road route",()->startActivityForResult(new Intent(this,RoutePlannerActivity.class),50));}
        try(LogStore store=new LogStore(this)){payload=payload(store);}catch(Exception e){payload=new JSONObject();}
        Spinner trips=new Spinner(this);List<String> labels=new ArrayList<>();labels.add("All trips");TreeSet<String> names=new TreeSet<>();
        for(String key:new String[]{"places","routes","plans"}){JSONArray rows=payload.optJSONArray(key);if(rows!=null)for(int i=0;i<rows.length();i++){String name=rows.optJSONObject(i).optString("trip");if(!name.isEmpty())names.add(name);}}
        labels.addAll(names);trips.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));trips.setSelection(Math.max(0,labels.indexOf(filter)));page.addView(trips);
        TextView note=new TextView(this);note.setText(R.string.travel_map_note);note.setTextColor(AdventureStyle.TEXT);note.setTextSize(12);note.setPadding(dp(12),dp(5),dp(12),dp(6));page.addView(note);
        web=new WebView(this);page.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        configureWebView();
        trips.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){filter=position==0?"":labels.get(position);js("filterTrip("+JSONObject.quote(filter)+")");if(pickingStop)js("pickStop()");}});
        web.loadUrl("https://appassets.androidplatform.net/map/index.html");
    }
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(){
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setGeolocationEnabled(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString()+" PoultonsRVLogbook/1.6 (+https://github.com/Gixxar/RV-App)");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();
                if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())){
                    String path=u.getPath();
                    if(path!=null&&Arrays.asList("/map/index.html","/map/leaflet.js","/map/leaflet.css","/map/map.js").contains(path))try{
                        String type=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":"text/html";
                        return new WebResourceResponse(type,"UTF-8",getAssets().open(path.substring(1)));
                    }catch(IOException ignored){}
                }
                if("https".equals(u.getScheme())&&"tile.openstreetmap.org".equals(u.getHost())&&!request.isForMainFrame())return null;
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return navigate(request.getUrl());}
            @Override public void onPageFinished(WebView view,String url){if(url.equals("https://appassets.androidplatform.net/map/index.html")){js("setLogbook("+payload.toString()+")");js("filterTrip("+JSONObject.quote(filter)+")");if(pickingStop)js("pickStop()");}}
        });
    }
    boolean navigate(Uri u){
        if("poulton".equals(u.getScheme())){
            String action=u.getHost();Intent data=new Intent();
            if(pickingStop&&!"stop".equals(action))return true;
            try{
                if("pin".equals(action)||"stop".equals(action)||"open".equals(action)){
                    double lat=Double.parseDouble(u.getQueryParameter("lat")),lon=Double.parseDouble(u.getQueryParameter("lon"));
                    if(!GeoData.valid(lat,lon))throw new IllegalArgumentException();
                    String name=u.getQueryParameter("name");if(name==null)name="";
                    if("open".equals(action)){openExternal(Uri.parse("geo:"+lat+","+lon+"?q="+Uri.encode(lat+","+lon+"("+name+")")));return true;}
                    data.putExtra("Latitude",String.valueOf(lat)).putExtra("Longitude",String.valueOf(lon)).putExtra("Name",name);
                }else if("route".equals(action)){
                    String points=u.getQueryParameter("points");JSONArray segments=GeoData.parse(points);
                    if(GeoData.pointCount(segments)>500)throw new IllegalArgumentException();
                    data.putExtra("Route points",segments.toString());
                }else if("edit".equals(action)){
                    Module module=Module.valueOf(u.getQueryParameter("module"));if(module!=Module.PLACES&&module!=Module.ROUTES&&module!=Module.PLANS)throw new IllegalArgumentException();
                    data.putExtra("module",module.name()).putExtra("id",Long.parseLong(u.getQueryParameter("id")));
                }else return true;
                result(action,data);
            }catch(Exception e){Toast.makeText(this,"Could not read this map selection",Toast.LENGTH_SHORT).show();}
            return true;
        }
        if("https".equals(u.getScheme())&&"www.openstreetmap.org".equals(u.getHost()))openExternal(u);
        return true;
    }
    private void openExternal(Uri uri){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){Toast.makeText(this,"No maps/browser app is available",Toast.LENGTH_SHORT).show();}}
    private void result(String action,Intent data){data.putExtra("action",action);setResult(RESULT_OK,data);finish();}
    private void control(LinearLayout row,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(AdventureStyle.DISPLAY);b.setTextColor(AdventureStyle.INK);b.setBackground(AdventureStyle.pressable(this,AdventureStyle.CARD,AdventureStyle.BORDER));b.setMinHeight(dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(b,lp);b.setOnClickListener(v->action.run());}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density);}
    private void js(String call){if(web!=null)web.evaluateJavascript("window."+call,null);}
    private void findPlace(){
        EditText input=new EditText(this);input.setHint("Town, postcode or address");input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Find a place").setMessage("Search uses your Android location-search provider. Confirm the result on the map before saving.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Search",(d,w)->{
            String query=input.getText().toString().trim();if(query.isEmpty())return;
            if(!Geocoder.isPresent()){Toast.makeText(this,"Search unavailable. Tap Add place and choose a position on the map.",Toast.LENGTH_LONG).show();return;}
            Toast.makeText(this,"Searching…",Toast.LENGTH_SHORT).show();
            search.execute(()->{
                List<Address> results;
                try{results=new Geocoder(getApplicationContext(),Locale.getDefault()).getFromLocationName(query,5);}catch(Exception e){results=Collections.emptyList();}
                List<Address> found=results==null?Collections.emptyList():results;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(found.isEmpty()){Toast.makeText(this,"No results. Try a nearby town or tap a position on the map.",Toast.LENGTH_LONG).show();return;}
                    String[] names=new String[found.size()];for(int i=0;i<names.length;i++)names[i]=found.get(i).getAddressLine(0)==null?query:found.get(i).getAddressLine(0);
                    new AlertDialog.Builder(this).setTitle("Choose a location").setItems(names,(dialog,index)->{
                        Address a=found.get(index);js("showSearch("+a.getLatitude()+","+a.getLongitude()+","+JSONObject.quote(names[index])+")");
                    }).setNegativeButton("Cancel",null).show();
                });
            });
        }).show();
    }
    static JSONObject payload(LogStore store)throws JSONException{
        JSONObject root=new JSONObject();JSONArray places=new JSONArray(),routes=new JSONArray(),plans=new JSONArray();
        for(LogStore.Entry e:store.list(Module.PLACES)){JSONObject d=e.data;places.put(new JSONObject().put("id",e.id).put("name",d.optString("Name")).put("trip",d.optString("Trip")).put("date",d.optString("Date")).put("lat",d.getDouble("Latitude")).put("lon",d.getDouble("Longitude")));}
        for(LogStore.Entry e:store.list(Module.ROUTES)){JSONObject d=e.data;routes.put(new JSONObject().put("id",e.id).put("name",d.optString("Name")).put("trip",d.optString("Trip")).put("date",d.optString("Date")).put("type",d.optString("Route type")).put("segments",GeoData.parse(d.getString("Route points"))));}
        for(LogStore.Entry e:store.list(Module.PLANS)){JSONObject d=e.data;plans.put(new JSONObject().put("id",e.id).put("name",d.optString("Name")).put("trip",d.optString("Trip")).put("date",d.optString("Date")).put("type",RvRouting.description(d)).put("segments",GeoData.parse(d.getString("Route points"))));}
        return root.put("places",places).put("routes",routes).put("plans",plans);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==50&&result==RESULT_OK)recreate();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("filter",filter);}
    @Override protected void onDestroy(){search.shutdownNow();if(web!=null){((LinearLayout)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
}
