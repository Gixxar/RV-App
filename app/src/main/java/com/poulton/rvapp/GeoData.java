package com.poulton.rvapp;

import org.json.*;

/** Validated WGS84 track segments. Gaps between GPX segments are never joined. */
public final class GeoData {
    public static final int MAX_POINTS=20000;
    private GeoData() {}
    public static boolean valid(double lat,double lon) {
        return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;
    }
    public static JSONArray parse(String encoded) throws JSONException {
        JSONArray segments=new JSONArray(encoded);int total=0;
        if(segments.length()==0||segments.length()>500)throw new JSONException("Use 1–500 route segments");
        for(int s=0;s<segments.length();s++){
            JSONArray points=segments.getJSONArray(s);
            if(points.length()<2)throw new JSONException("Each route segment needs at least two points");
            for(int i=0;i<points.length();i++){
                JSONArray p=points.getJSONArray(i);
                if(p.length()!=2||!(p.get(0) instanceof Number)||!(p.get(1) instanceof Number)||!valid(p.getDouble(0),p.getDouble(1)))throw new JSONException("Invalid route coordinates");
                if(++total>MAX_POINTS)throw new JSONException("Route exceeds 20,000 points");
            }
        }
        return segments;
    }
    public static int pointCount(JSONArray segments) throws JSONException {
        int n=0;for(int i=0;i<segments.length();i++)n+=segments.getJSONArray(i).length();return n;
    }
    public static double miles(JSONArray segments) throws JSONException {
        double total=0;
        for(int s=0;s<segments.length();s++){
            JSONArray pts=segments.getJSONArray(s);
            for(int i=1;i<pts.length();i++){
                JSONArray a=pts.getJSONArray(i-1),b=pts.getJSONArray(i);
                double lat1=Math.toRadians(a.getDouble(0)),lat2=Math.toRadians(b.getDouble(0));
                double dl=lat2-lat1,dx=Math.toRadians(b.getDouble(1)-a.getDouble(1));
                double h=Math.pow(Math.sin(dl/2),2)+Math.cos(lat1)*Math.cos(lat2)*Math.pow(Math.sin(dx/2),2);
                total+=3958.7613*2*Math.asin(Math.sqrt(Math.min(1,h)));
            }
        }
        return total;
    }
}
