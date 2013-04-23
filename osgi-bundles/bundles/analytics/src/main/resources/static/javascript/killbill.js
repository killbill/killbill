/*
*  'killbillGraph' is the namespace required to access all the public objects
*
*
*  Input are expected to be of the form:
*   dataForGraph = [ {"name":"line1", "values":[{"x":"2013-01-01", "y":6}, {"x":"2013-01-02", "y":6}] },
*                    {"name":"line2", "values":[{"x":"2013-01-01", "y":12}, {"x":"2013-01-02", "y":3}] } ];
*
*   There can be up to 20 lines -- limited by the color palette -- per graph; the graph can be either:
*   - layered graph (KBLayersGraph)
*   - lines graph (KBLinesGraph)
*
*   Description of the fields:
*   - name is the 'name of the line-- as shown in the label
*   - values are the {x,y} coordinates for each point; the x coordinates should be dates and should all be the same for each entries.
*
*/
(function(killbillGraph, $, undefined) {

  /**
  * Input parameters to draw all the graphs
  */
  killbillGraph.KBInputGraphs = function(canvasWidth, canvasHeigth, topMargin, rightMargin, bottomMargin, leftMargin, betweenGraphMargin, graphData) {

      this.topMargin = topMargin;
      this.rightMargin = rightMargin;
      this.bottomMargin = bottomMargin;
      this.leftMargin = leftMargin;


      this.betweenGraphMargin = betweenGraphMargin;

      this.canvasWidth = canvasWidth;
      this.canvasHeigth = canvasHeigth;

      this.linesData = graphData[0];
      this.layersData = graphData[1];
  }


  /**
  * KBGraph : Base class for both layered and non layered graphs
  */
  killbillGraph.KBGraph = function(graphCanvas, data, width, heigth, palette) {

      this.graphCanvas = graphCanvas;
      this.data = data;
      this.width = width;
      this.heigth = heigth;

      // the palette function out of which we create color map
      this.palette = palette;


      /**
      * Create the 'x' date scale
      * - dataX is is an ordered array of all the dates
      */
      this.getScaleDate = function() {

          var dataX = this.extractKeyOrValueFromDataLayer(this.data[0], 'x');
          var minDate = new Date(dataX[0]);
          var maxDate = new Date(dataX[dataX.length - 1]);
          return d3.time.scale().domain([minDate, maxDate]).range([0, width]);
      }

      /**
      * Create the 'Y' axis in a new svg group
      * - scaleY is the d3 scale built based on height and y point range
      */
      this.createYAxis = function(scaleY) {
          var yAxisLeft = d3.svg.axis().scale(scaleY).ticks(4).orient("left");
          this.graphCanvas.append("svg:g")
                  .attr("class", "y axis")
                  .attr("transform", "translate(-25,0)")
                .call(yAxisLeft);
      }

      /**
      * Create the 'X' axis in a new svg group
      * - dataLayer : the data for the layer forma
      * - xAxisGraphGroup the group where this axis will be attached to
      * - xAxisHeightTick the height of the ticks
      */
      this.createXAxis = function(xAxisGraphGroup, xAxisHeightTick) {

          var scaleX = this.getScaleDate();
          var xAxis = d3.svg.axis().scale(scaleX).tickSize(- xAxisHeightTick).tickSubdivide(true);
          xAxisGraphGroup.append("svg:g")
                 .attr("class", "x axis")
               .call(xAxis);
      }


      /**
       * Add the cirles for each point in the graph line
       *
       * This is used for both stacked and non stacked lines
       */
       this.addCirclesForGraph = function(circleGroup, lineId, dataX, dataY, scaleX, scaleY, lineColor) {
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
      * Extract the 'x' or 'y' from dataLyer format where each entry if of the form:
      * - attr is either the 'x' or 'y'
      * - dataLayer : the data for a given layer
      * E.g:
      *  "name": "crescendo",
      *  "values": [
      *     { "x": "2010-07-08", "y":  0},
      *     ....
      */
      this.extractKeyOrValueFromDataLayer = function(dataLayer, attr) {
          var result = [];
          for (var i = 0; i < dataLayer.values.length; i++) {
              result.push(dataLayer.values[i][attr])
          }
          return result;
      }


      /**
      * Add on the path the name of the line -- not used anymore as we are using external labels
      */
      this.addPathLabel = function(graph, lineId, positionPercent) {
           graph.append("svg:g")
                .append("text")
                .attr("font-size", "15")
                .append("svg:textPath")
                .attr("xlink:href", "#" + lineId)
                .attr("startOffset", positionPercent)
                .text(function(d) { return lineId; });
       }



      /**
      * Create a new group for the circles-- no translations needed
      */
      this.createCircleGroup = function(canvas, lineId) {
          return this.graphCanvas.append("svg:g")
              .attr("id", "circles-" + lineId);
      }

      /**
      * Given a colorMap, extract the k-ieme color
      *
      * The colormap are standard d3 colormap which swicth to new color every 4 colors;
      * in order to maximize the difference among colors we first get colors that are far apar
      *
      */
      this.getColor = function(k) {
          var div = Math.floor(k / 4);
          var mod = k % 4;
          var value = div + 4 * mod;
          return this.colorMap[value];
      }

      /**
      *  Create the color map from the d3 palette
      */
      this.createColorMap = function() {
          var colorMap = {}
          for (var i = 0; i < 20; i++) {
              colorMap[i] = this.palette(i);
          }
          return colorMap;
      }

      /**
      * Add the list label on the 'legend'
      */

      this.addLabels = function(labelId, translateY) {

          var $divLabelLayers = $('<div id=' + labelId +'  style="margin-top:' + translateY + 'px"></div>');
          var $labelList = $('<ul></ul>');
          $divLabelLayers.append($labelList);

          $("#legend").append($divLabelLayers);
          for (var i = 0; i < this.data.length; i++) {
              this.addLabel($labelList, this.getColor(i), this.data[i].name);
          }
      }

      /**
      * Add a given label
      */
      this.addLabel = function(labelList, color, labelName) {

          var $labelListItem =  $('<li style="list-style-type: none">');
          var $divLabelListItem = $('<div id=label-"' + labelName + '" class="swatch" style="background-color:' + color + '"></div>');
          var $spanListItemName = $('<span>' + labelName + '</span> ');

          $labelListItem.append($divLabelListItem);
          $labelListItem.append($spanListItemName);
          labelList.append($labelListItem);
      }

      /**
      * Attach handlers to all circles so as to display value
      *
      * Note that this will attach for all graphs-- not only the one attached to that objec
      */
      this.addMouseOverCircleForValue = function() {

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

      /* Build and save colorMap */
      this.colorMap = this.createColorMap();
  }

  /**
  *  KBLayersGraph : Inherits KBGraph abd offers specifities for layered graphs
  */
  killbillGraph.KBLayersGraph = function(graphCanvas, data, width, heigth, palette) {

     killbillGraph.KBGraph.call(this, graphCanvas, data, width, heigth, palette);



     /**
     * Create the area function that defines for each point in the stack graph
     * its x, y0 (offest from previous stacked graph) and y position
     */
     this.createLayerArea = function(scaleX, scaleY) {
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
     * Create the 'y' scale for the stack graph
     *
     * Extract min/max for each x value across all layers
     *
     */
     this.getLayerScaleValue = function() {

         var tmp = [];
         for (var i = 0; i < this.data.length; i++) {
             tmp.push(this.data[i].values)
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
         return d3.scale.linear().domain([minValue,  maxValue]).range([heigth, 0]);
     }


     /**
     * All all layers on the graph
     */
     this.addLayers = function(stack, area, dataLayers) {

        var dataLayerStack = stack(dataLayers);

        var currentObj = this;

        this.graphCanvas.selectAll("path")
             .data(dataLayerStack)
             .enter()
             .append("path")
             .style("fill", function(d,i) {
                 return currentObj.getColor(i);
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
     this.drawStackLayers = function() {

        var scaleX = this.getScaleDate();
        var scaleY = this.getLayerScaleValue();

          var stack = d3.layout.stack()
              .offset("zero")
              .values(function(d) { return d.values; });

          var area = this.createLayerArea(scaleX, scaleY);

          this.addLayers(stack, area, this.data);

          var dataX = this.extractKeyOrValueFromDataLayer(this.data[0], 'x');
          var dataY0 = null;
          for (var i = 0; i < this.data.length; i++) {

              var circleGroup = this.createCircleGroup(this.data[i]['name']);
              var dataY = this.extractKeyOrValueFromDataLayer(this.data[i], 'y');
              if (dataY0) {
                  for (var k = 0; k < dataY.length; k++) {
                      dataY[k] = dataY[k] + dataY0[k];
                  }
              }
              this.addCirclesForGraph(circleGroup, this.data[i]['name'], dataX, dataY, scaleX, scaleY, this.getColor(i));
              dataY0 = dataY;
          }

          this.createYAxis(scaleY);
     }

  }
  killbillGraph.KBLayersGraph.prototype = Object.create(killbillGraph.KBGraph.prototype);



  /**
  *  KBLayersGraph : Inherits KBGraph abd offers specifities for layered graphs
  */
  killbillGraph.KBLinesGraph = function(graphCanvas, data, width, heigth, palette) {

     killbillGraph.KBGraph.call(this, graphCanvas, data, width, heigth, palette);

     /**
     * Create the 'y' scale for line graphs (non stacked)
     */
     this.getScaleValue = function() {

         var dataYs = [];
         for (var k=0; k<this.data.length; k++) {
             var dataY = this.extractKeyOrValueFromDataLayer(this.data[k], 'y');
             dataYs.push(dataY);
         }

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
          return d3.scale.linear().domain([minValue, maxValue]).range([this.heigth, 0]);
      }

      /**
      * Add the svg line for this data (dataX, dataY)
      */
      this.addLine = function(dataY, scaleX, scaleY, lineColor, lineId) {

          var dataX = this.extractKeyOrValueFromDataLayer(this.data[0], 'x');
          this.graphCanvas.selectAll("path.line")
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

            var circleGroup = this.createCircleGroup(lineId);
            this.addCirclesForGraph(circleGroup, lineId, dataX, dataY, scaleX, scaleY, lineColor);
      }


      /**
      * Draw all lines
      * It will create its Y axis
      */
      this.drawLines = function() {

          var scaleX = this.getScaleDate();
          var scaleY = this.getScaleValue();

          for (var k=0; k<this.data.length; k++) {
              var dataY = this.extractKeyOrValueFromDataLayer(this.data[k], 'y');
              this.addLine(dataY, scaleX, scaleY, this.getColor(k), this.data[k]['name']);
          }
          this.createYAxis(scaleY);
       }

  }
  killbillGraph.KBLinesGraph.prototype = Object.create(killbillGraph.KBGraph.prototype);


  killbillGraph.GraphStructure = function() {

      /**
      * Setup the main divs for both legend and main charts
      *
      * It is expected to have a mnain div anchir on the html with id = 'chartAnchor'.
      */
      this.setupDomStructure = function() {

          var $divLegend = $('<div id="legend" class="legend">');
          //var $divLegendText = $('<h1>Killbill Data</h1>');
          //$divLegend.append($divLegendText);

           var $divChart = $('<div id="charts" class="charts">');
           var $spanChart = $('<span id="chartId" class="charts"></span>');
           $divChart.prepend($spanChart);

           $("#chartAnchor").append($divLegend);
           $("#chartAnchor").append($divChart);
      }


      /**
      * Create initial canvas on which to draw all graphs
      */
      this.createCanvas = function(m, w, h) {
          return d3.select("#chartId")
                    .append("svg:svg")
                    .attr("width", w + m[1] + m[3])
                    .attr("height", h + m[0] + m[2]);
      }


      /**
      * Create a new group and make the translation to leave room for margins
      */
      this.createCanvasGroup = function(canvas, translateX, translateY) {
          return canvas
                .append("svg:g")
                .attr("transform", "translate(" + translateX + "," + translateY + ")");
      }
  };

} (window.killbillGraph = window.killbillGraph || {}, jQuery));
