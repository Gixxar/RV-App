package com.poulton.rvapp;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Dimension-aware truck costing as a conservative RV planning aid. Never falls back to car. */
public final class RvRouting {
    private RvRouting() {}
    public static final String TYPE="Planned RV route";
    public static JSONObject request(JSONArray stops,JSONObject profile)throws JSONException{
        RouteService.stops(stops.toString());JSONObject rv=VehicleProfile.normalized(profile);JSONArray locations=new JSONArray();
        for(int i=0;i<stops.length();i++){JSONObject p=stops.getJSONObject(i);locations.put(new JSONObject().put("lat",p.getDouble("lat")).put("lon",p.getDouble("lon")).put("type","break").put("radius",500).put("search_cutoff",500));}
        JSONObject truck=new JSONObject().put("height",rv.getDouble("Height m")).put("length",rv.getDouble("Length m")).put("width",rv.getDouble("Width m")).put("weight",rv.getDouble("Weight kg")/1000)
            .put("axle_load",rv.optDouble("Max axle load kg",0)/1000).put("axle_count",rv.optInt("Axle count",5)).put("ignore_restrictions",false).put("ignore_access",false);
        return new JSONObject().put("locations",locations).put("costing","truck").put("costing_options",new JSONObject().put("truck",truck)).put("format","osrm").put("shape_format","geojson").put("units","kilometers").put("directions_type","none");
    }
    public static JSONObject calculate(JSONArray stops,JSONObject profile)throws Exception{
        JSONObject data=request(stops,profile);RouteService.throttle();
        HttpURLConnection connection=(HttpURLConnection)new URL("https://valhalla1.openstreetmap.de/route").openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(25000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent","PoultonsRVLogbook/1.7 (+https://github.com/Gixxar/RV-App)");connection.setRequestProperty("X-Client-Id","github.com/Gixxar/RV-App");connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        byte[] body=data.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(body.length);
        try{
            try(OutputStream out=connection.getOutputStream()){out.write(body);}
            int code=connection.getResponseCode();if(code!=200)throw new IOException("RV routing unavailable or no matching route (HTTP "+code+"). Your stops are kept; no car route was substituted.");
            try(InputStream in=connection.getInputStream()){return response(in,stops,profile);}
        }finally{connection.disconnect();}
    }
    public static JSONObject response(InputStream in,JSONArray stops,JSONObject profile)throws Exception{
        JSONObject normalized=VehicleProfile.normalized(profile);ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>4*1024*1024)throw new IOException("RV route response too large");bytes.write(buffer,0,n);}
        JSONObject root=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));JSONArray warnings=root.optJSONArray("warnings");if(warnings!=null&&warnings.length()>0)throw new IOException("The routing provider adjusted or rejected a setting. Check the RV measurements and try again; no route was accepted.");
        if(!"truck".equals(root.getJSONArray("routes").getJSONObject(0).optString("weight_name")))throw new IOException("Provider did not confirm truck routing. No RV route was accepted.");
        JSONObject result=RouteService.response(new ByteArrayInputStream(bytes.toByteArray()),stops);
        return result.put("Route type",TYPE).put("RV profile",normalized.toString());
    }
    public static String description(JSONObject plan){
        if(!TYPE.equals(plan.optString("Route type")))return "Car route · RV dimensions not applied";
        try{return "RV dimensions applied · "+VehicleProfile.summary(new JSONObject(plan.getString("RV profile")))+" · restrictions may be incomplete";}catch(Exception e){return "RV route profile unavailable · recalculate";}
    }
}
