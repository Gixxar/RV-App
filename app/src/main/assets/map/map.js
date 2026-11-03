'use strict';
const map=L.map('map',{worldCopyJump:true}).setView([54,-2],5);
const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',keepBuffer:1,updateWhenIdle:true}).addTo(map);
const places=L.featureGroup().addTo(map),tracks=L.featureGroup().addTo(map),sketches=L.featureGroup().addTo(map),plans=L.featureGroup().addTo(map);
L.control.layers(null,{'Visited places':places,'GPX tracks / routes':tracks,'Manual connections':sketches,'Route plans':plans}).addTo(map);
let data={places:[],routes:[],plans:[]},pickingStop=false,filter='',mode='',points=[],draft=null,searchMarker=null;
const hint=document.getElementById('hint');
function status(s){hint.textContent=s;}
function go(action,args){const q=new URLSearchParams(args);window.location.href='poulton://'+action+'?'+q.toString();}
function titleNode(text){const d=document.createElement('strong');d.textContent=text;return d;}
function actionButton(label,action,args){const b=document.createElement('button');b.textContent=label;b.onclick=()=>go(action,args);return b;}
function matches(item){return !filter||item.trip===filter;}
function popup(item,module){const d=document.createElement('div');d.appendChild(titleNode(item.name));const p=document.createElement('p');p.textContent=[item.date,item.trip,item.type].filter(Boolean).join(' · ');d.appendChild(p);d.appendChild(actionButton('Edit record','edit',{module,id:item.id}));if(module==='PLACES')d.appendChild(actionButton('Open in Maps','open',{lat:item.lat,lon:item.lon,name:item.name}));return d;}
function rebuild(){places.clearLayers();tracks.clearLayers();sketches.clearLayers();plans.clearLayers();data.places.filter(matches).forEach(p=>L.circleMarker([p.lat,p.lon],{radius:8,color:'#17272d',fillColor:'#ad9672',fillOpacity:1,weight:2}).bindPopup(popup(p,'PLACES')).addTo(places));data.routes.filter(matches).forEach(r=>L.polyline(r.segments,{color:r.type!=='Manual waypoints'?'#246c54':'#956e30',weight:4,dashArray:r.type!=='Manual waypoints'?null:'8 7'}).bindPopup(popup(r,'ROUTES')).addTo(r.type!=='Manual waypoints'?tracks:sketches));(data.plans||[]).filter(matches).forEach(r=>L.polyline(r.segments,{color:'#406fa0',weight:4,dashArray:'10 5'}).bindPopup(popup(r,'PLANS')).addTo(plans));status(places.getLayers().length+tracks.getLayers().length+sketches.getLayers().length+plans.getLayers().length===0?'No saved places or routes for this trip yet. Add a place, draw a route or import GPX.':'Tap a pin or line for details. Solid green: GPX file. Dashed gold: manual connections. Dashed blue: plans; RV suitability not checked.');}
window.setLogbook=function(d){data=d;rebuild();fitAll();};
window.filterTrip=function(value){filter=value;rebuild();fitAll();};
function fitAll(){const all=L.featureGroup([places,tracks,sketches,plans]);const bounds=all.getBounds();if(bounds.isValid())map.fitBounds(bounds.pad(.15),{maxZoom:14});else map.setView([54,-2],5);}
window.fitAll=fitAll;
function cancelMode(){mode='';points=[];if(searchMarker){map.removeLayer(searchMarker);searchMarker=null;}if(draft){map.removeLayer(draft);draft=null;}document.getElementById('draw').style.display='none';status('Tap a pin or line for details.');}
window.pickStop=function(){cancelMode();pickingStop=true;mode='stop';status('Tap the map to choose a route stop. This does not record a visit.');};
window.addPlace=function(){cancelMode();mode='pin';status('Tap the map where you visited. Confirm the position, then save.');};
window.drawRoute=function(){cancelMode();mode='route';document.getElementById('draw').style.display='flex';status('Tap points along your route. These are straight connections, not road routing.');};
function redraw(){if(draft)map.removeLayer(draft);draft=L.polyline(points,{color:'#956e30',dashArray:'8 7',weight:4}).addTo(map);status(points.length+' manual points. Add points, undo, or save.');}
function placeCandidate(lat,lon,name){if(searchMarker)map.removeLayer(searchMarker);const box=document.createElement('div');box.appendChild(titleNode(name||'Visited place'));const coords=document.createElement('p');coords.textContent=lat.toFixed(6)+', '+lon.toFixed(6);box.appendChild(coords);box.appendChild(actionButton(pickingStop?'Use as route stop':'Save visited place',pickingStop?'stop':'pin',{lat,lon,name:name||''}));searchMarker=L.circleMarker([lat,lon],{radius:10,color:'#17272d',fillColor:'#ad9672',fillOpacity:1}).addTo(map).bindPopup(box).openPopup();}
window.showSearch=function(lat,lon,name){cancelMode();map.setView([lat,lon],14);placeCandidate(lat,lon,name);};
map.on('click',e=>{const lat=e.latlng.lat,lon=((e.latlng.lng+180)%360+360)%360-180;if(mode==='pin'||mode==='stop')placeCandidate(lat,lon,'');if(mode==='route'){if(points.length>=500){status('Maximum 500 manual points. Save this route before drawing another.');return;}points.push([+lat.toFixed(6),+lon.toFixed(6)]);redraw();}});
document.getElementById('save').onclick=()=>{if(points.length<2){status('Add at least two route points.');return;}go('route',{points:JSON.stringify([points])});};
document.getElementById('undo').onclick=()=>{points.pop();redraw();};document.getElementById('cancel').onclick=cancelMode;
tiles.on('tileerror',()=>status('Background map unavailable. Check your internet connection. Saved pins and tracks remain available.'));
