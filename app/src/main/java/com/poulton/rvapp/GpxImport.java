package com.poulton.rvapp;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.json.*;
import java.io.*;

/** Bounded GPX track/route importer; declarations and entities are rejected. */
public final class GpxImport {
    private GpxImport() {}
    public static JSONObject read(InputStream input) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){if(bytes.size()+n>5*1024*1024)throw new IOException("GPX file exceeds 5 MB");bytes.write(buffer,0,n);}
        XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
        p.setInput(new ByteArrayInputStream(bytes.toByteArray()),null);
        JSONArray segments=new JSONArray(),current=null;String name="",date="",time="";int count=0,depth=0,nameDepth=-1,timeDepth=-1;boolean root=false,inTrack=false,inRoute=false,hasTrack=false;
        for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.nextToken()){
            if(event==XmlPullParser.DOCDECL)throw new IOException("GPX declarations are not supported");
            if(event==XmlPullParser.ENTITY_REF){
                String entity=p.getName();
                if(!java.util.Arrays.asList("amp","lt","gt","apos","quot").contains(entity)&&!entity.startsWith("#"))throw new IOException("GPX custom entities are not supported");
                if(nameDepth==depth&&p.getText()!=null)name+=p.getText();
            }
            if(event==XmlPullParser.START_TAG){
                depth++;String tag=p.getName();
                if(depth==1){root="gpx".equals(tag);if(!root)throw new IOException("Choose a GPX file");}
                if("trk".equals(tag)){inTrack=true;hasTrack=true;}
                if("rte".equals(tag)){inRoute=true;current=new JSONArray();}
                if("trkseg".equals(tag)&&inTrack)current=new JSONArray();
                if("name".equals(tag)&&name.isEmpty()&&(inTrack||inRoute))nameDepth=depth;
                if("time".equals(tag)&&date.isEmpty()&&(inTrack||inRoute)){timeDepth=depth;time="";}
                if(("trkpt".equals(tag)&&inTrack||"rtept".equals(tag)&&inRoute)&&current!=null){
                    double lat=Double.parseDouble(p.getAttributeValue(null,"lat")),lon=Double.parseDouble(p.getAttributeValue(null,"lon"));
                    if(!GeoData.valid(lat,lon))throw new IOException("GPX contains invalid coordinates");
                    if(++count>GeoData.MAX_POINTS)throw new IOException("GPX exceeds 20,000 points");
                    current.put(new JSONArray().put(lat).put(lon));
                }
            }else if(event==XmlPullParser.TEXT){if(nameDepth==depth)name+=p.getText();if(timeDepth==depth)time+=p.getText();
            }else if(event==XmlPullParser.END_TAG){
                String tag=p.getName();
                if(("trkseg".equals(tag)||"rte".equals(tag))&&current!=null){if(current.length()>=2)segments.put(current);current=null;}
                if("trk".equals(tag))inTrack=false;if("rte".equals(tag))inRoute=false;
                if(depth==timeDepth){try{date=java.time.OffsetDateTime.parse(time.trim()).toLocalDate().toString();}catch(RuntimeException ignored){}timeDepth=-1;}
                if(depth==nameDepth)nameDepth=-1;depth--;
            }
        }
        if(!root)throw new IOException("Choose a GPX file");GeoData.parse(segments.toString());
        return new JSONObject().put("Name",name.trim().isEmpty()?"Imported journey":name.trim()).put("Route type",hasTrack?"GPX track":"GPX route").put("Route points",segments.toString()).put("Date",date);
    }
}
