// 
// SPDX-FileCopyrightText: 2015-2023 Bill Ross Photoriot <phobrain@sonic.net>
//
// SPDX-License-Identifier: FSFAP
//
//  Functions used in Phobrain pages.
//
//  Most are copied/modified snippets of code from
//      web searches. No ownership is claimed or
//      responsibility assumed for correctness.

function getPlatform() {
    var userAgent = navigator.userAgent || navigator.vendor || window.opera;
    if (userAgent.includes("Android")) {
        return "Android";
    }
    if (navigator.platform.includes("armv")) {
        return "Android";
    }
    if (userAgent.includes("iPhone")) {
        return "iOS";
    }
    if (userAgent.includes("iPad")) {
        return "iOS";
    }
    //alert("ua " + userAgent + " platf " + navigator.platform);
    return "desktop";
}

var platform = getPlatform();

var isAndroid = platform == "Android";
var isIOS = platform == "iOS";
var hasTouch = ('ontouchstart' in window) ||
        (window.DocumentTouch &&
            window.document instanceof DocumentTouch) ||
        window.navigator.maxTouchPoints ||
        window.navigator.msMaxTouchPoints ? true : false;

console.log("phobasic.platform " + platform + " .hasTouch " + hasTouch);

// crossbrowser version
function getCoords(elem)
{
  var box = elem.getBoundingClientRect();

  var body = document.body;
  var docEl = document.documentElement;

  var scrollTop = window.pageYOffset || docEl.scrollTop || body.scrollTop;
  var scrollLeft = window.pageXOffset || docEl.scrollLeft || body.scrollLeft;

  var clientTop = docEl.clientTop || body.clientTop || 0;
  var clientLeft = docEl.clientLeft || body.clientLeft || 0;

  var top  = box.top +  scrollTop - clientTop;
  var left = box.left + scrollLeft - clientLeft;

  return { top: Math.round(top), left: Math.round(left) };
}

function getUrlVars()
{
  var vars = {};
  var parts = window.location.href.replace(/[?&]+([^=&]+)=([^&]*)/gi, function(m,key,value) {
    vars[key] = value;
  });
  return vars;
}

function setCookie(cname, cvalue)
{
  console.log("setCookie " + cname + " " + cvalue);
  var d = new Date();
  var exdays = 5000;
  d.setTime(d.getTime() + (exdays*24*60*60*1000));
  var expires = "expires="+d.toUTCString();
  document.cookie = cname + "=" + cvalue + "; " + expires +
        "; sameSite=Strict";
}

function getCookie(cname)
{
  var name = cname + "=";
  var ca = document.cookie.split(';');
  for(var i=0; i<ca.length; i++) {
    var c = ca[i];
    while (c.charAt(0)==' ') c = c.substring(1);
    if (c.indexOf(name) == 0) return c.substring(name.length,c.length);
  }
  return "";
}

Base64 = (function () {
    var digitsStr =
    //   0       8       16      24      32      40      48      56     63
    //   v       v       v       v       v       v       v       v      v
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-";
    var digits = digitsStr.split('');
    var digitsMap = {};
    for (var i = 0; i < digits.length; i++) {
        digitsMap[digits[i]] = i;
    }
    return {
        fromInt: function(int32) {
            var result = '';
            while (true) {
                result = digits[int32 & 0x3f] + result;
                int32 >>>= 6;
                if (int32 === 0)
                    break;
            }
            return result;
        },
        toInt: function(digitsStr) {
            var result = 0;
            var digits = digitsStr.split('');
            for (var i = 0; i < digits.length; i++) {
                result = (result << 6) + digitsMap[digits[i]];
            }
            return result;
        }
    };
})();

function getHTTP()
{
  var xmlHttp;
  try {
    // Firefox, Opera 8.0+, Safari
    xmlHttp = new XMLHttpRequest();
  } catch (e) {
    // Internet Explorer
    try {
      xmlHttp = new ActiveXObject("Msxml2.XMLHTTP");
    } catch (e) {
      try {
        xmlHttp = new ActiveXObject("Microsoft.XMLHTTP");
      } catch (e) {
        throw("Your browser does not support AJAX!");
      }
    }
  }
  return xmlHttp;
}

function base64Str(arr)
{
  if (arr.length == 0) {
    return "";
  }
  var str = "";
  for (var i=0; i<arr.length; i++) {
    str += Base64.fromInt(arr[i]);
    str += ",";
  }
  return str;
}

// decStr() 0'th elt is size
function decStr(arr)
{
    if (arr.length == 0) return "0";

    var str = "" + (-1 * arr.length) + ",";
    for (var i=0; i<arr.length; i++) {
        str += arr[i];
        str += ",";
    }
//console.log('decStr ' + str);
    return str;
}

var lastX = 0;
var lastY = 0;

var mousedownX = null;
var mousedownY = null;

function setLastXY(event)
{
  event = event || window.event; // IE-ism

  // If pageX/Y aren't available and clientX/Y are,
  // calculate pageX/Y - logic taken from jQuery.
  // (This is to support old IE)
  if (event.pageX == null && event.clientX != null) {
    eventDoc = (event.target && event.target.ownerDocument) || document;
    doc = eventDoc.documentElement;
    body = eventDoc.body;

    event.pageX = event.clientX +
              (doc && doc.scrollLeft || body && body.scrollLeft || 0) -
              (doc && doc.clientLeft || body && body.clientLeft || 0);
    event.pageY = event.clientY +
              (doc && doc.scrollTop  || body && body.scrollTop  || 0) -
              (doc && doc.clientTop  || body && body.clientTop  || 0 );
  }
  // Use event.pageX / event.pageY here
  //var rate = 0;
  lastX = event.pageX;
  lastY = event.pageY;

  if (inImage && mouseDown) {
    // console.log("inimage/MDOWN XY " + lastX + " " + lastY);
    mousedownX = lastX;
    mousedownY = lastY;
  }
}

// getLox - return array of [ loc, locspec ]

function getLox(img)
{
    var loc = "unk";
    var locspec = "unk";

    setLastXY(new Event("getLox"));

	if (mousedownX == null  ||  mousedownY == null) {
		// console.log('getLox: mousedownXY null, default LTc');
		return [ 'LTc', locspec ];
	}

	var crds = getCoords(img);

	var right = crds.left + img.width;
	var bottom = crds.top + img.height;
	var centerX = (crds.left + right) / 2;
	var centerY = (crds.top + bottom) / 2;

    // quadrants

	loc = "";
	if (mousedownX < centerX) {
		loc += "L";
	} else {
		loc += "R";
	}
	if (mousedownY < centerY) {
		loc += "T";
	} else {
		loc += "B";
	}

    // Bill's version of center

	var centerXleft = centerX - img.width / 6;
	if (mousedownX > centerXleft) {
		var centerXright = centerX + img.width / 6;
		if (mousedownX < centerXright) {
			var centerYtop = centerY - img.height / 6;
			if (mousedownY > centerYtop) {
				var centerYbottom = centerY + img.height / 6;
				if (mousedownY < centerYbottom) {
					loc += "c";
				}
			}
		}
	}

    var locnums = [ crds.top, bottom, crds.left, right,
                    mousedownX, mousedownY ];

    locspec = base64Str(locnums).trim();

	console.log("getLox: top " + crds.top + " bottom " + bottom + 
			"\nleft " + crds.left + " right " + right +
			"\nX " + mousedownX + " Y " + mousedownY +
			"\nloc " + loc + "  locspec " + locspec);

	return [loc, locspec];
}

navigator.browserSpecs = (function(){
    var ua= navigator.userAgent, tem, 
    M= ua.match(/(opera|chrome|safari|firefox|msie|trident(?=\/))\/?\s*(\d+)/i) || [];
    if(/trident/i.test(M[1])){
        tem=  /\brv[ :]+(\d+)/g.exec(ua) || [];
        return {name:'IE',version:(tem[1] || '')};
    }
    if(M[1]=== 'Chrome'){
        tem= ua.match(/\b(OPR|Edge)\/(\d+)/);
        if(tem!= null) return {name:tem[1].replace('OPR', 'Opera'),version:tem[2]};
    }
    M= M[2]? [M[1], M[2]]: [navigator.appName, navigator.appVersion, '-?'];
    if((tem= ua.match(/version\/(\d+)/i))!= null) M.splice(1, 1, tem[1]);
    return {name:M[0],version:M[1]};
})();

(function($){
    $.fn.isActive = function(){
        return $(this.get(0)).hasClass('open')
    }
})(jQuery)

var mdown_time = null;

function mdown() {
//alert('mdown');
  mdown_time = new Date();
}

function getMD(e)
{

console.log("getMD retry " + retry0Ct);

  //e.preventDefault();

  var xmlHttp;
  try {
     xmlHttp = getHTTP();
  } catch (e) {
     alert(e);
     return false;
  }
  xmlHttp.onreadystatechange=function() {
    if(xmlHttp.readyState==4) {
      if (xmlHttp.status == 200) {
         md = JSON.parse(xmlHttp.responseText);
         console.log("md.credit rcvd: " + md.credit);
         console.log("md.kw rcvd: " + md.kw);
         showMD();
      } else if (xmlHttp.status == 401) {
        console.log("401: " + xmlHttp.responseText);
        callAlertTooMuch("Unknown user");
      } else if (xmlHttp.status == 500) {
        wipeSession();
        callAlertTooMuch("Server error, try reloading page.");
      }
    }
  };
  try {
    var params = "sess=" + sessionID;
    switch (tileId) {
      case 0:
        params += "&p1=x&p2=x\n"; break;
      case 1:
      case 2:
        params += "&p1=1." + vDepth[1] +
                  "&p2=2." + vDepth[2] + "\n";
        break;
      case 3:
        params += "&p1=1." + vDepth[3] +
                  "&p2=2." + vDepth[4] + "\n";
        break;
      default:
        alert("unexp tileId: " + tileId);
        break;
    }

console.log("md params for getkext: " + params);
    var url = servletRoot + "/getkext";
    xmlHttp.open("POST", url, true);
    xmlHttp.setRequestHeader("Content-type",
                               "application/x-www-form-urlencoded");
    xmlHttp.send(params);
  } catch (e) {
    alert(e);
    return false;
  }
  return true;
}

var mouseDownNextToOptions = false;
var mouseDownOnCredit = false;

// hides field for MD/Credit/methods
function hideMDCredit() {
  mouseDownNextToOptions = false;
  mouseDownOnCredit = false;
  var td = document.getElementById("showId");
  if (td != null) td.innerHTML = "&nbsp;";
}

var mdK = null;
var md = null;

var retry0Ct = 0;

function showMD() {

  console.log("showMD");
  if (!mouseDownNextToOptions &&  !mouseDownOnCredit) return;
  console.log("showMD 4real");

  if (tileId == 0) {
    if (md == null) {
console.log("md is null retry " + retry0Ct);
      retry0Ct++;
      if (retry0Ct > 2) {
        retry0Ct = 0;
        return;
      }
      getMD();
      return;
    }
    retry0Ct = 0;

  } else {

    var ii = 1;
    if (tileId == 3) ii = 3;

    var ps = "0." + vDepth[ii] + "1." + vDepth[ii+1];
    if (md == null  ||  ps != mdK) {
      mdK = ps;
      getMD();
      return;
    }
  }
  var content;
  if (mouseDownOnCredit) {
    content = md.credit;
  } else {
    content = md.kw;
  }
  console.log("content [" + content.trim() + "]");
  var td = document.getElementById("showId");
  if (td == null) {
    alert("Bad element id: showId");
  } else {
    var fsize = 20;
    //if (hasTouch) fsize = 80;
    var spanx = "<span style=\"" +
		"color:rgba(255,255,255,0.4);" +
        "font-family:'Courier New',Courier,monospace;" +
		"font-size:" + fsize + "pt;" +
        "text-align:right;" +
        "\">" +
			content.trim() +
		"</span>";
console.log("spanx |" + spanx + "|");
    td.innerHTML = spanx;
  }
}
