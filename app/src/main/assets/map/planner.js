'use strict';
const map=L.map('map').setView([54,-2],5);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',keepBuffer:1,updateWhenIdle:true}).addTo(map);
const overlay=L.featureGroup().addTo(map);
window.showPlan=function(data){overlay.clearLayers();if(data.segments)L.polyline(data.segments,{color:'#406fa0',weight:4,dashArray:'10 5'}).addTo(overlay);(data.stops||[]).forEach((p,i)=>{if(typeof p.lat!=='number'||typeof p.lon!=='number')return;const text=document.createElement('span');text.textContent=(i+1)+'. '+p.name;L.circleMarker([p.lat,p.lon],{color:'#193d33',fillColor:'#b49a67',fillOpacity:1,radius:7}).bindPopup(text).addTo(overlay);});const bounds=overlay.getBounds();if(bounds.isValid())map.fitBounds(bounds.pad(.15),{maxZoom:15});else map.setView([54,-2],5);};
