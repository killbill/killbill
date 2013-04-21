
/**
* Extract the 'x' or 'y' from dataLyer format
* where each entry if of the form:
*
*  "name": "crescendo",
*  "values": [
*     { "x": "2010-07-08", "y":  0},
*     ....
*/
function extractKeyOrValueFromDataLayer(dataLayer, attr) {
    var result = [];
    for (var i = 0; i < dataLayer.values.length; i++) {
        result.push(dataLayer.values[i][attr])
    }
    return result;
}


/**
* Create the 'x' scale -- with x being a date
*/
function getScaleDate(dataX, w) {
    var minDate = new Date(dataX[0]);
    var maxDate = new Date(dataX[dataX.length - 1]);
    return d3.time.scale().domain([minDate, maxDate]).range([0, w]);
}

/**
* Create the 'y' scale for line graphs (non stacked)
*/
function getScaleValue(dataYs, h) {
     var minValue = 0;
     var maxValue = 0;
     for (var i=0; i<dataYs.length; i++) {
         for (var j = 0; j < dataYs[i].length; j++) {
             if (dataYs[i][j] < minValue) {
                 minValue = dataYs[i][j];
             }
             if (dataYs[i][j] > maxValue) {
                 maxValue = dataYs[i][j];
             }
         }
     }
     if (minValue > 0) {
         minValue = 0;
     }
     return d3.scale.linear().domain([minValue, maxValue]).range([h, 0]);
 }

/**
* Create the 'y' scale for the stack graph
*
* Extract min/max for each x value across all layers
* 
*/
function getStackedScaleValue(dataLayers, h) {

    var tmp = [];
    for (var i = 0; i < dataLayers.length; i++) {
        tmp.push(dataLayers[i].values)
    }

    var sumValues = [];
    for (var i = 0; i < tmp[0].length; i++) {
        var max = 0;
        for (var j = 0; j < tmp.length; j++) {
              max = max + tmp[j][i].y;
        }
        sumValues.push(max);
    }
    var minValue = 0;
    var maxValue = 0;
    for (var i = 0; i < sumValues.length; i++) {
        if (sumValues[i] < minValue) {
            minValue = sumValues[i];
        }
        if (sumValues[i] > maxValue) {
            maxValue = sumValues[i];
        }
    }
    if (minValue > 0) {
        minValue = 0;
    }
    return d3.scale.linear().domain([minValue,  maxValue]).range([h, 0]);
}


/**
* Create the 'X' axis in a new svg group
*/
function createXAxis(graph, scaleX, h, xAxisHeightTick) {
    var xAxis = d3.svg.axis().scale(scaleX).tickSize(- xAxisHeightTick).tickSubdivide(true);
    graph.append("svg:g")
           .attr("class", "x axis")
           .attr("transform", "translate(0," + h + ")")
         .call(xAxis);
}

/**
* Create the 'Y' axis in a new svg group
*/
function createYAxis(graph, scaleY) {
    var yAxisLeft = d3.svg.axis().scale(scaleY).ticks(4).orient("left");
    graph.append("svg:g")
            .attr("class", "y axis")
            .attr("transform", "translate(-25,0)")
          .call(yAxisLeft);
}

/**
* Create the area function that defines for each point in the stack graph
* its x, y0 (offest from previous stacked graph) and y position
*/
function createLayerArea(scaleX, scaleY) {
    var area = d3.svg.area()
          .x(function(d) {
              return scaleX(new Date(d.x));
          })
          .y0(function(d) {
              return scaleY(d.y0);                
          })
          .y1(function(d) {
              return scaleY(d.y + d.y0);
          });
    return area;
}

/**
* All all layers on the graph
*/
function addLayers(graph, colorMapLayers, stack, area, dataLayers) {
    
   var dataLayerStack = stack(dataLayers);
   
   graph.selectAll("path")
        .data(dataLayerStack)
        .enter()
        .append("path")
        .style("fill", function(d,i) {
            return getColor(colorMapLayers, i);
         }).attr("d", function(d) {
            return area(d.values);
         })
         .attr("id", function(d) {
             return d.name;
         });    
}

/**
* Draw all layers-- calls previous function addLayers
* It will create its Y axis
*/
function drawStackLayers(graph, margins, colorMapLayers, dataLayers, w, h, xAxisHeightTick) {

      // Compute scales
      var dataX = extractKeyOrValueFromDataLayer(dataLayers[0], 'x');
      var scaleX = getScaleDate(dataX, w);
      var scaleY = getStackedScaleValue(dataLayers, h);

     var stack = d3.layout.stack()
         .offset("zero")
         .values(function(d) { return d.values; });

     var area = createLayerArea(scaleX, scaleY);

     addLayers(graph, colorMapLayers, stack, area, dataLayers);

     var dataY0 = null;
     for (var i = 0; i < dataLayers.length; i++) {

         var circleGroup = createCircleGroup(graph, dataLayers[i]['name'], margins[3], margins[0]);
         var dataY = extractKeyOrValueFromDataLayer(dataLayers[i], 'y');
         if (dataY0) {
             for (var k = 0; k < dataY.length; k++) {
                 dataY[k] = dataY[k] + dataY0[k];
             }
         }
         addCirclesForGraph(circleGroup, dataLayers[i]['name'], dataX, dataY, scaleX, scaleY, getColor(colorMapLayers, i));
         dataY0 = dataY;
     }

     createYAxis(graph, scaleY);
}

/**
* Add the svg line for this data (dataX, dataY)
*/
function addLine(graph, margins, dataX, dataY, scaleX, scaleY, lineColor, lineId) {
     graph.selectAll("path.line")
           .data([dataY])
           .enter()
           .append("svg:path")
           .attr("d", d3.svg.line()
               .x(function(d,i) {
                 return scaleX(new Date(dataX[i]));
               })
               .y(function(d) {
                 return scaleY(d);
               }))
           .attr("id", lineId)
           .style("stroke", lineColor);
           
      var circleGroup = createCircleGroup(graph, lineId, margins[3], margins[0] );
      addCirclesForGraph(circleGroup, lineId, dataX, dataY, scaleX, scaleY, lineColor);
}


/**
* Draw all lines
* It will create its Y axis
*/
function drawLines(graph, margins, colorMapLines, linesData, w, h) {
      
    var dataX = linesData[0]["dates"]
    var dataYs = [];
    for (var i = 0; i < linesData.length; i++) { dataYs.push(linesData[i]["values"]); }
    
    var scaleX = getScaleDate(dataX, w);
    var scaleY = getScaleValue(dataYs, h);

    for (var k=0; k<dataYs.length; k++) {
        addLine(graph, margins, dataX, dataYs[k], scaleX, scaleY, getColor(colorMapLines, k), linesData[k]['name']);
    }              
    createYAxis(graph, scaleY);
 }
 

 /**
 * Add the cirles for each point in the graph line
 * 
 * This is used for both stacked and non stacked lines
 */
 function addCirclesForGraph(circleGroup, lineId, dataX, dataY, scaleX, scaleY, lineColor) {
      var node = circleGroup.selectAll("circles") 
          .data(dataY) 
          .enter() 
          .append("svg:g");
          
     /* First we add the circle */      
     node.append("svg:circle") 
     .attr("id", function(d, i) { 
           return "circle-" + lineId + "-" + i; })
     .attr("cx", function(d, i) { 
         return scaleX(new Date(dataX[i]));
      }) 
     .attr("cy", function(d, i) { 
         return scaleY(d);
      }) 
     .attr("r", 3)
     .attr("fill", lineColor)
     .attr("value", function(d, i) { 
           return d;
     });
     
     /* Second we do another pass and add the overlay text for value */
     node.append("svg:text")
         .attr("x",  function(d, i) { 
               return scaleX(new Date(dataX[i]));
         })
         .attr("y", function(d, i) { 
            return scaleY(d);
          })
         .attr("class", "overlay")
         .attr("display", "none")      
         .text(function(d, i) { 
             return "value = " + d;
          });
} 

/**
* Add on the path the name of the line -- not used anymore as we are using external labels
*/
function addPathLabel(graph, lineId, positionPercent) {
     graph.append("svg:g")
          .append("text")
          .attr("font-size", "15")
          .append("svg:textPath")
          .attr("xlink:href", "#" + lineId)
          .attr("startOffset", positionPercent)
          .text(function(d) { return lineId; });               
 }

/**
* Create initial canvas on which to draw all graphs
*/
function createCanvas(m, w, h) {
    return d3.select("#chartId")
              .append("svg:svg")
              .attr("width", w + m[1] + m[3])
              .attr("height", h + m[0] + m[2]);
}

/**
* Create a new group and make the translation to leave room for margins
*/
function createCanvasGroup(canvas, translateX, translateY) {
    return canvas
          .append("svg:g")
          .attr("transform", "translate(" + translateX + "," + translateY + ")");
}

/**
* Create a new group for the circles-- no translations needed
*/
function createCircleGroup(canvas, lineId, translateX, translateY) {
    return canvas.append("svg:g")
        .attr("id", "circles-" + lineId);
}

/**
* Given a colorMap, extract the k-ieme color
*
* The colormap are standard d3 colormap which swicth to new color every 4 colors;
* in order to maximize the difference among colors we first get colors that are far apart
* 
*/
function getColor(colorMap, k) {
    var div = Math.floor(k / 4);
    var mod = k % 4;
    var value = div + 4 * mod;
    return colorMap[value];
}

/**
*  Create the color map from the d3 palette
*/
function createColorMap(palette) {
    var colorMap = {}
    for (var i = 0; i < 20; i++) {
        colorMap[i] = palette(i);
    }
    return colorMap;         
}

/**
* Add the list label on the 'legend'
*/

function addLabels(linesOrLayers, labelId, colorMap, className) {
    
    var $divLabelLayers = $('<div id=' + labelId +'  class=' + className + ' ></div>');
    var $labelList = $('<ul></ul>');  
    $divLabelLayers.append($labelList);
    
    $("#legend").append($divLabelLayers);
    for (var i = 0; i < linesOrLayers.length; i++) {
        addLabel($labelList, getColor(colorMap, i), linesOrLayers[i]);
    }
}

/**
* Add a given label
*/
function addLabel(labelList, color, labelName) {

    var $labelListItem =  $('<li style="list-style-type: none">'); 
    var $divLabelListItem = $('<div id=label-"' + labelName + '" class="swatch" style="background-color:' + color + '"></div>'); 
    var $spanListItemName = $('<span>' + labelName + '</span> '); 
     
    $labelListItem.append($divLabelListItem);
    $labelListItem.append($spanListItemName);
     
    labelList.append($labelListItem);
}

/**
* Setup the main divs for both legend and main charts
*
* It is expected to have a mnain div anchir on the html with id = 'chartAnchor'.
*/
function setupDomStructure() {

    var $divLegend = $('<div id="legend" class="legend">');            
    var $divLegendText = $('<h1>Killbill Data</h1>');
     $divLegend.append($divLegendText);
     
     var $divChart = $('<div id="charts" class="charts">');
     var $spanChart = $('<span id="chartId" class="charts"></span>');
     $divChart.prepend($spanChart);
     
     $("#chartAnchor").append($divLegend);    
     $("#chartAnchor").append($divChart);     
}


/**
* Attach handlers to all circles so as to display value
*/
function addMouseOverCircleForValue() {
    $('circle').each(function(i) { 
        
        var circleGroup = $(this).parent();
        var circleText = circleGroup.find('text').first();   

        $(this).hover(function() {
            circleText.show();
        }, function() {
            circleText.hide();
        });
    });
}

/**
* Draw the dashboard based on the data provided.
*/
function drawAll(ajaxLinesData, ajaxLayersData) {
    
    
    var colorMapLines = createColorMap(d3.scale.category20b());
    var colorMapLayers = createColorMap(d3.scale.category20c());
                  
    var margins = [80, 80, 20, 80];
    var width = 1000 - margins[1] - margins[3];
    var heigth = 800 - margins[0] - margins[2];
    
    setupDomStructure();
    
    var canvas = createCanvas(margins, width, heigth + (margins[0] + margins[2]) );
    
    var lineCanvas = createCanvasGroup(canvas, margins[3], margins[0]);
    
    var linesDataValues = [];
    for (var i = 0; i < ajaxLinesData.length; i++) { linesDataValues.push(ajaxLinesData[i]["values"]); }
    drawLines(lineCanvas, margins, colorMapLines, ajaxLinesData, width, heigth / 2);
    
    var stackCanvas = createCanvasGroup(canvas, margins[3], (heigth / 2) + 2 * margins[0] );
    drawStackLayers(stackCanvas, margins, colorMapLayers, ajaxLayersData, width, heigth / 2, (heigth + margins[0]));
    
    var dataX = extractKeyOrValueFromDataLayer(ajaxLayersData[0], 'x');
    var scaleX = getScaleDate(dataX, width);
    
    var xAxisCanvaGroup = createCanvasGroup(canvas, margins[3], (heigth / 2) + 2 * margins[0]);
    createXAxis(xAxisCanvaGroup, scaleX, heigth / 2, (heigth + margins[0]));
    
    var lines = []
    for (var i = 0; i < ajaxLinesData.length; i++) { lines.push(ajaxLinesData[i]["name"]) }  
    addLabels(lines, "labelsLine",  colorMapLines, "labelLines");
        
    var layers = [];
    for (var i = 0; i < ajaxLayersData.length; i++) { layers.push(ajaxLayersData[i]["name"]) }  
    addLabels(layers, "labelsLayer" , colorMapLayers, "labelLayers");
      
    addMouseOverCircleForValue();
 }

/**
* Ajax call to retrieve the data from the server
*/
function doGetData(server, port, analyticsPrefix, endpoint, queryParams, fn) {
    var request_url = "http://" + server + ":" + port + analyticsPrefix + endpoint;
    var first = true;
    if (queryParams) {

        var queryKeys = Object.keys(queryParams);
        for (var i = 0; i < queryKeys.length; i++) {
            var curKey = queryKeys[i];
            var delim = first ? "?" : "&";
            request_url = request_url + "?" + curKey + "=" +  queryParams[curKey]
            first = false;
        }
    }

    console.log("request_url " + request_url);

    return $.ajax({
      type: "GET",
      contentType: "application/json",
      url: request_url
    }).done(function(data) {
       console.log("Done " + request_url + " with data " + data.length);
       fn(data);
    });
}

/**
* Entry point where we specify server and port to retrieve data can call drawlAll()
*/
function fetchDataAndDrawAll(server, port) {

    $().ready(function() {

        var layersData;
        var linesData;

        $.when(doGetData(server, port, "/plugins/killbill-analytics", "/planTransitionsOverTime", null, function(data) {
            linesData = data;
        }), doGetData(server, port, "/plugins/killbill-analytics", "/recurringRevenueOverTime", null, function(data) {
            layersData = data;
        })).done(function() {
              console.log( 'I fire once BOTH ajax requests have completed: linesData = ' + linesData.length);
              console.log( 'I fire once BOTH ajax requests have completed: layersData = ' + layersData.length);
              drawAll(linesData, layersData);
          })
          .fail(function() {
            console.log( 'I fire if one or more requests failed.' );
          });
    });
}
