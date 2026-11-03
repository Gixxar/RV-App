package com.poulton.rvapp;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Ordered car routing. No GPS recording or RV clearance/access checks. */
public final class RouteService {
    private RouteService() {}
    public static final String TYPE="Planned road route";
    public static final String NOTICE="Standard car routing. RV height, width, weight, length and road access are not checked. Check restrictions and campsite entrances before driving.";
    private static long lastRequest;
    public static JSONArray stops(String encoded)throws JSONException{
        JSONArray points=new JSONArray(encoded);
        if(points.length()<2||points.length()>10)throw new JSONException("Choose a start, destination and up to 8 intermediate stops");
        for(int i=0;i<points.length();i++){
            JSONObject p=points.getJSONObject(i);
            if(!(p.get("lat") instanceof Number)||!(p.get("lon") instanceof Number)||!GeoData.valid(p.getDouble("lat"),p.getDouble("lon"))||!(p.get("name") instanceof String)||p.getString("name").length()>300)throw new JSONException("Invalid route stop");
            if(i>0){JSONObject before=points.getJSONObject(i-1);if(Math.abs(before.getDouble("lat")-p.getDouble("lat"))<.000001&&Math.abs(before.getDouble("lon")-p.getDouble("lon"))<.000001)throw new JSONException("Consecutive stops must be different places");}
        }
        return points;
    }
    public static URL url(JSONArray points)throws JSONException,MalformedURLException{
        stops(points.toString());StringJoiner coords=new StringJoiner(";"),radii=new StringJoiner(";");
        for(int i=0;i<points.length();i++){JSONObject p=points.getJSONObject(i);coords.add(p.getDouble("lon")+","+p.getDouble("lat"));radii.add("500");}
        return new URL("https://router.project-osrm.org/route/v1/driving/"+coords+"?overview=full&geometries=geojson&steps=false&alternatives=false&generate_hints=false&radiuses="+radii);
    }
    static synchronized void throttle()throws IOException{long now=android.os.SystemClock.elapsedRealtime();if(lastRequest!=0&&now-lastRequest<1100)throw new IOException("Please wait a moment before calculating again.");lastRequest=now;}
    public static JSONObject calculate(JSONArray points)throws Exception{
        URL url=url(points);
        throttle();
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent","PoultonsRVLogbook/1.6 (+https://github.com/Gixxar/RV-App)");connection.setRequestProperty("Accept","application/json");
        try{
            int status=connection.getResponseCode();
            if(status!=200)throw new IOException(status==429?"Routing service is busy. Try again later.":"Routing service unavailable (HTTP "+status+"). Your stops are kept.");
            try(InputStream in=connection.getInputStream()){return response(in,points);}
        }finally{connection.disconnect();}
    }
    public static JSONObject response(InputStream in,JSONArray requested)throws IOException,JSONException{
        stops(requested.toString());ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(bytes.size()+n>4*1024*1024)throw new IOException("Route response too large. Plan shorter sections.");bytes.write(buffer,0,n);}
        JSONObject root=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        if(!"Ok".equals(root.optString("code")))throw new IOException("No road route found. Choose road entrances closer to your stops or plan separate sections.");
        JSONArray waypoints=root.getJSONArray("waypoints");if(waypoints.length()!=requested.length())throw new JSONException("Incomplete route stops");
        for(int i=0;i<waypoints.length();i++){
            JSONObject w=waypoints.getJSONObject(i);JSONArray location=w.getJSONArray("location");
            if(location.length()!=2||!GeoData.valid(location.getDouble(1),location.getDouble(0))||!Double.isFinite(w.getDouble("distance"))||w.getDouble("distance")<0||w.getDouble("distance")>500)throw new JSONException("Route stop could not be matched near its chosen position");
        }
        JSONObject route=root.getJSONArray("routes").getJSONObject(0);double distance=route.getDouble("distance"),seconds=route.getDouble("duration");
        if(!Double.isFinite(distance)||!Double.isFinite(seconds)||distance<=0||seconds<=0||distance>100_000_000||seconds>10_000_000||route.getJSONArray("legs").length()!=requested.length()-1)throw new JSONException("Invalid route estimate");
        JSONObject geometry=route.getJSONObject("geometry");if(!"LineString".equals(geometry.getString("type")))throw new JSONException("Invalid route shape");
        JSONArray source=geometry.getJSONArray("coordinates"),line=new JSONArray();
        if(source.length()>GeoData.MAX_POINTS)throw new JSONException("Route exceeds 20,000 points. Plan shorter sections.");
        for(int i=0;i<source.length();i++){JSONArray p=source.getJSONArray(i);if(p.length()!=2||!(p.get(0) instanceof Number)||!(p.get(1) instanceof Number))throw new JSONException("Invalid route coordinate");line.put(new JSONArray().put(p.getDouble(1)).put(p.getDouble(0)));}
        JSONArray segments=new JSONArray().put(line);GeoData.parse(segments.toString());
        return new JSONObject().put("Route type",TYPE).put("Route stops",requested.toString()).put("Route points",segments.toString()).put("Distance miles",String.format(Locale.ROOT,"%.2f",distance/1609.344)).put("Drive minutes",String.format(Locale.ROOT,"%.1f",seconds/60));
    }
    public static void validatePlan(Map<String,String> values)throws JSONException{
        stops(values.getOrDefault("Route stops",""));
        if(RvRouting.TYPE.equals(values.get("Route type")))VehicleProfile.normalized(new JSONObject(values.getOrDefault("RV profile","")));
        else if(!TYPE.equals(values.get("Route type")))throw new JSONException("Calculate a road route first");
        for(String key:new String[]{"Distance miles","Drive minutes"}){double n=Double.parseDouble(values.getOrDefault(key,"0"));if(!Double.isFinite(n)||n<=0)throw new JSONException("Missing route estimate");}
    }
    public static UriParts leg(JSONArray stops,int index)throws JSONException{
        stops(stops.toString());if(index<0||index>=stops.length()-1)throw new JSONException("Choose a route leg");
        JSONObject a=stops.getJSONObject(index),b=stops.getJSONObject(index+1);
        return new UriParts(a.getDouble("lat")+","+a.getDouble("lon"),b.getDouble("lat")+","+b.getDouble("lon"));
    }
    public static final class UriParts {public final String origin,destination;UriParts(String a,String b){origin=a;destination=b;}}
}
