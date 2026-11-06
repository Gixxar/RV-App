package com.poulton.rvapp;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Campsite positions belong to the campsite, never to the visited-place log. */
public final class CampsiteLocations {
    private CampsiteLocations() {}
    private static final String NUMBER="(-?\\d+(?:\\.\\d+)?)";
    private static final Pattern PLACE=Pattern.compile("!3d"+NUMBER+"!4d"+NUMBER);
    private static final Pattern QUERY=Pattern.compile("[?&](?:q|query)="+NUMBER+","+NUMBER+"(?:&|$)");
    public static double[] position(JSONObject data){
        try{double lat=Double.parseDouble(data.optString("Latitude")),lon=Double.parseDouble(data.optString("Longitude"));return GeoData.valid(lat,lon)?new double[]{lat,lon}:null;}
        catch(NumberFormatException e){return null;}
    }
    public static double[] fromLink(String link){
        if(link==null)return null;
        // @lat,lon is the camera centre, not necessarily the campsite. Never use it.
        for(Pattern pattern:new Pattern[]{PLACE,QUERY}){
            Matcher m=pattern.matcher(link);if(m.find())try{double lat=Double.parseDouble(m.group(1)),lon=Double.parseDouble(m.group(2));if(GeoData.valid(lat,lon))return new double[]{lat,lon};}catch(NumberFormatException ignored){}
        }
        return null;
    }
    public static void set(JSONObject data,double lat,double lon,String source)throws JSONException{
        if(!GeoData.valid(lat,lon))throw new IllegalArgumentException("Invalid campsite coordinates");
        data.put("Latitude",Double.toString(lat)).put("Longitude",Double.toString(lon)).put("Location source",source);
    }
    public static String query(JSONObject data){return (data.optString("Name")+" "+data.optString("Address")).trim();}
    /** Do not resurrect a removed record or overwrite a location/name changed during lookup. */
    public static boolean saveSearch(LogStore store,long id,String query,double lat,double lon)throws JSONException{
        for(LogStore.Entry current:store.list(Module.CAMPSITES))if(current.id==id){
            if(position(current.data)!=null||!query(current.data).equals(query))return false;
            set(current.data,lat,lon,"Search result — check location");store.save(Module.CAMPSITES,id,current.data);return true;
        }
        return false;
    }
    public static List<LogStore.Entry> missing(LogStore store){
        List<LogStore.Entry> result=new ArrayList<>();for(LogStore.Entry e:store.list(Module.CAMPSITES))if(position(e.data)==null)result.add(e);return result;
    }
}
