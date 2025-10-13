
// phodots.js is about feeling/flow in feel.html
// vs. pairs as in curate.html.

// noisy debug on console
var logDots = true;
var logSkips = false;
var logColor = false;
var logUpdateXY = false;
var logGibber = false;
var logHist = false;


// set in html
var requireRating = false;
var inImage = 0;
var imageIn = -1;
var dotSpeed = 0;

var dotColor = "white";
var colorDraw = true;

var watchDots = false;
var waitingDotty = 0;

var lastGibberStart = null;

function toggleWatchDots()
{
    watchDots = !watchDots;
    waitingDotty = 0;

    if (watchDots) {
        document.getElementById("menu_radio_watch_dots").innerHTML =
            "Dots: don't wait";
    } else {
        document.getElementById("menu_radio_watch_dots").innerHTML =
            "Dots: 2nd click";
    }
}

function toggleColorDraw()
{
    colorDraw = !colorDraw;
    if (colorDraw) {
        document.getElementById("menu_radio_color_draw").innerHTML =
            "Draw: use grey";
    } else {
        document.getElementById("menu_radio_color_draw").innerHTML =
            "Draw: use colors";
    }
}

function vecAngle( dY, dX)
{
    var t = (180/Math.PI) * Math.atan2( dY , dX ); // -180..180
    while (t < 0) t += 360;
    return t;
}

// reset by reDot()
var dotList = [];
var d2angleHist = new Array(36).fill(0); // per 2 consecutive dots, absolute
var d3angleHist = new Array(36).fill(0); // per 3 consecutive dots, relative/centered

var gibIndex = 0;
var dotGibberTimer = null;

var cacheDots = false;

function reDot()
{
    if (logDots) console.log("REDOT");
	clearInterval(dotGibberTimer);
	dotGibberTimer = null;
	dotSpeed = 0;

	if (!cacheDots) {
		var dotCount = dotList.length;
		for (var i=0; i<dotCount; i++) {
            var dot = dotList[i];
            if (!dot.mouseUp) document.body.removeChild(dotList[i]);
		}
	}
//console.log(d3angleHist);
//console.log(d2angleHist);
	gibIndex = 0;
	dotList = [];
	spiralRGB = null;
	d3angleHist = new Array(36).fill(0);
	d2angleHist = new Array(36).fill(0);
}

function dotSide(dot)
{
    var side = 1;
    if (tileId == 3) { // stacked
        if (dot.y > window.innerHeight / 2) { // very? approx
            return 4;
        }
        return 3;
    }
    if (dot.x > window.innerWidth / 2) {
        return 2;
    }
    return 1;
}

var methid = 0;

function swapDot(somenum, dot1, dot2)
{
	switch(methid) {
	case 0:
		var num = somenum % 3;
		if (num == 0  ||  num == 2) {
			var tmp = dot1.style.backgroundColor;
			dot1.style.backgroundColor = dot2.style.backgroundColor;
			dot2.style.backgroundColor = tmp;
		}
		if (num == 1  ||  num == 2) {
			var tmp = dot1.size;
			dot1.size = dot2.size;
			var dim = dot1.size + 'px';
			if (somenum % 3) {
				dot1.style.height = dim;
			}
			if (somenum % 2) {
				dot1.style.width = dim;
			}

			dot2.size = tmp;
			dim = dot2.size + 'px';
			// just do one for wobble; size will correct it
			if (somenum % 2) {
				dot2.style.height = dim;
			}
			if (somenum % 3) {
				dot2.style.width = dim;
			}
		}
		break;
	case 1:
	default:
		var tmp = dot1.style.backgroundColor;
		dot1.style.backgroundColor = dot2.style.backgroundColor;
		dot2.style.backgroundColor = tmp;

		tmp = dot1.size;
		dot1.size = dot2.size;
		var dim = dot1.size + 'px';
		dot1.style.width = dim;
		dot1.style.height = dim;

		dot2.size = tmp;
		dim = dot2.size + 'px';
		// just do one for wobble; size will correct it
		if (somenum % 2) {
			dot2.style.height = dim;
		}
		if (somenum % 3) {
			dot2.style.width = dim;
		}

		break;
	}
}

function startDotGibber(slow)
{
	clearInterval(dotGibberTimer);
	dotGibberTimer = null;

	if (dotList.length < 2) return;

	gibIndex = 0;

	var msg = "";
	var delay;

	if (slow) {
		var aDot = dotList[Math.floor(dotList.length*Math.random())];
		if (aDot.vel > 90) {
			msg = 'a.vel>90';
			delay = 800;
		} else {
			var acc = Math.sqrt(Math.abs(aDot.accel));
			if (acc > 5) {
				acc = 3;
			}
			msg = 'a.vel<90,a=' + acc;
			delay = Math.floor(600 + acc * 100);
		}
	} else {
		msg = 'time/draw';
		delay = 400 + (dotList[dotList.length-1].time % 200)
			/ dotSpeed; // rel to dotList[0].time;
	}
    delay = Math.floor(delay);
    if (logGibber) console.log('GIBBER: ' + msg + ': ' + delay);
	dotGibberTimer = setInterval(function() { dotGibber(); }, delay);
	lastGibberStart = new Date();
}

function dotGibber()
{
//var t0 = new Date();

	var dotCount = dotList.length;
	if (new Date() % 2) {
		//console.log('MOD2 dc ' + dotCount);
		if (dotCount % 2) {
			var bound = dotCount / 2;
			for (var i=0; i<bound; i++) {
				var dot = dotList[i];
				var next = dotList[dotCount - 1 - i];
				swapDot(Math.floor((Math.random() * 10) + 1),
						dot, next);
			}
		} else {
			for (var i=0; i<dotCount; i++) {
				var dot = dotList[i];
				var next = i + 1;
				if (next == dotCount) {
					next = 0;
				}
				next = dotList[next];
				swapDot(Math.floor((Math.random() * 10) + 1),
						dot, next);
			}
		}
	} else {
		//console.log('MOD1 dc ' + dotCount);
		if (dotCount % 2) {
			for (var i=dotCount-1; i>-1; i--) {
				var dot = dotList[i];
				var next = i - 1;
				if (next == -1) {
					next = dotCount-1;
				}
				next = dotList[next];

				swapDot(Math.floor((Math.random() * 10) + 1),
						dot, next);
//console.log('dot.size ' + dot.size + ' N ' + dotCount + ' next ' + next);
			}
		} else {
			var bound = Math.floor(dotCount / 2);
			for (var i=0; i<bound; i++) {
				var dot = dotList[i];
				var next = dotList[dotCount - 1 - i];

				swapDot(Math.floor((Math.random() * 10) + 1),
						dot, next);
			}
		}
	}
	if (lastGibberStart != null) {
		var r = Math.random();
		var impatient = 2500 + Math.floor(r * 2500);
		if (new Date() - lastGibberStart > impatient) {
			startDotGibber(r<0.5);
		}
	}

/*
	var mult1 = 1.25;
	var mult2 = 0.8;
	if (dotCount % 2) {
		mult1 = 0.8;
		mult2 = 1.25;
	}
	for (var i=0; i<dotCount; i++) {
		var dot = dotList[i];
		//document.body.removeChild(dotList[i]);
		var mult = mult1;
		if (i%2) {
			mult = mult2;
		}
		dot.size = Math.ceil(mult * dot.size);

		var dim = dot.size + 'px';

		dot.style.width = dim;
		dot.style.height = dim;
	}
*/
//console.log('gib ' + (new Date()-t0));
}


function limitURIDots(params)
{
	if (params.length < 7000) { // leaving 1192
		return params;
	}
	var start = params.length - 1;
	var end = start;
	var ct = 0;
	while (end > 7000) {
		for (var i=0; i<3; i=i+1) { // x,y,dt
			end = params.lastIndexOf(',', end-1);
		}
		ct += 1;
//console.log('bak from ' + end);
	}
console.log('trimmed ' + start + ' to ' + end + ' ct ' + ct + '/' + dotList.length);

	return params.substring(0, end);
}


function createLineElement(x, y, length, angle) {
    var line = document.createElement("div");
    var styles = 'border: 1px solid black; '
               + 'width: ' + length + 'px; '
               + 'height: 0px; '
               + '-moz-transform: rotate(' + angle + 'rad); '
               + '-webkit-transform: rotate(' + angle + 'rad); '
               + '-o-transform: rotate(' + angle + 'rad); '
               + '-ms-transform: rotate(' + angle + 'rad); '
               + 'position: absolute; '
               + 'top: ' + y + 'px; '
               + 'left: ' + x + 'px; ';
    line.setAttribute('style', styles);
    return line;
}

function createLine(x1, y1, x2, y2) {
    var a = x1 - x2,
        b = y1 - y2,
        c = Math.sqrt(a * a + b * b);

    var sx = (x1 + x2) / 2,
        sy = (y1 + y2) / 2;

    var x = sx - c / 2,
        y = sy;

    var alpha = Math.PI - Math.atan2(-b, a);

    return createLineElement(x, y, c, alpha);
}


// plain movement, return time
function updateXY(x, y, caller)
{
	var now = new Date();

    if (logUpdateXY &&  dialogFlowRating != null  &&  mouseDown) console.log('updateXY/' + caller + ': ' + x + ',' + y + ' flowRating ' + dialogFlowRating);

	if (inImage) {
		pixInPic++;
	} else {
		pixOutPic++;
	}

	lastX = x;
	lastY = y;

	accumX += lastX;
	accumY += lastY;
	accumCount++;

	if (accumCount == 1) {
		maxX = lastX;
		minX = lastX;
		maxY = lastY;
		minY = lastY;
	} else {
		if (lastX > maxX) {
			maxX = lastX;
		} else if (lastX < minX) {
			minX = lastX;
		}
		if (lastY > maxY) {
			maxY = lastY;
		} else if (lastY < minY) {
			minY = lastY;
		}
	}

	dotColor = "white";

	if (prevX != null  &&  prevT != null) {
		var dT = 1 + (now - prevT); // avoid 0

		var dx = prevX - lastX;
		var dy = prevY - lastY;
		var dist = Math.sqrt( dx*dx + dy*dy);
//alert('dt dist ' + dT + ' ' + dist);
		dTot += dist;
		var rate;
		rate = Math.round(100 * dist / dT);
		if (rate > maxVel) {
			maxVel = rate;
			distMaxVel = dTot;
			if (prevTime) {
				timeMaxVel = now - prevTime;
			} else {
				timeMaxVel = -1;
			}
		}
		var acc = rate - lastVel;
		if (acc > maxAcc) {
			maxAcc = acc;
		}
		if (acc < minAcc) {
			minAcc = acc;
		}
		var jerk = acc - lastAcc;
		if (jerk > maxJerk) {
			maxJerk = jerk;
			dotColor = "orange";
		}

		lastVel = rate;
		lastAcc = acc;
	}
	prevT = now;
	prevX = lastX;
	prevY = lastY;

	return now;
}

var prevT = null;
var prevX = null, prevY;
var rMax = 0;
//var rates = [];
var dTot = 0;
var maxVel = 0;
var distMaxVel = 0;
var timeMaxVel = 0;
var lastVel = 0;
var lastAcc = 0;
var maxAcc = 0;
var minAcc = 0;
var maxJerk = 0;
var maxX = 0;
var minX = 0;
var maxY = 0;
var minY = 0;
var lastX = 0;
var lastY = 0;
var accumX = 0;
var accumY = 0;
var accumCount = 0;

var picCount = 0;
var pixInPic = 0;
var pixOutPic = 0;
var prevTime;
var startNext;
var imageLoaded = -1;

function disableDragging(e) {
  e.preventDefault();
}

/*
// reset by reDot()
var dotList = [];
var d3angleHist = new Array(36).fill(0);
var d2angleHist = new Array(36).fill(0);

var cacheDots = false;
*/

var solidIncrease = true;

// rgbs initially = background
var plainRGB = [ parseInt(144), parseInt(144), parseInt(144), parseInt(80) ];

var rgbs = {};
// 'left' pic prev
rgbs['1.0'] = plainRGB;
// 'left' pic current
rgbs['1.1'] = plainRGB;
// 'right' pic prev
rgbs['2.0'] = plainRGB;
// 'right' pic current
rgbs['2.1'] = plainRGB;

function colorBaseArr(rgbarr, vel)
{
	//return colorBaseGoldenRatio(rgbarr);
	var x = Math.trunc(vel);

	if (logColor) console.log("colorBaseArr " + x);

	if (x < 3) return colorBaseCompl(rgbarr);
	if (x > 70) return colorBaseRand(rgbarr);

	//return colorBaseCompl(colorBaseGoldenRatio(rgbarr));
	if (x % 2 == 0) {
		if (x % 4 == 0) return colorBaseRand(rgbarr);
		return colorBaseCompl(rgbarr);
	}
	if (x % 3 == 0) return colorBaseGoldenRatio(rgbarr);
	if (x % 5 == 0) return colorBaseCompl(
					colorBaseGoldenRatio(rgbarr));
	return colorBaseCompl(rgbarr);
}

var golden_ratio_conjugate = 0.618033988749895;

function colorBaseGoldenRatio(rgbarr)
{
	var rgb = new Object();
	rgb.r = rgbarr[0];
	rgb.g = rgbarr[1];
	rgb.b = rgbarr[2];

	var hsv = RGB2HSV(rgb);
	if (logDots) console.log("SV " + hsv.saturation + " " + hsv.value);
	if (rgbarr[3] < 0.3) {
		if (logDots) console.log("Goose saturation for radius " + rgbarr[3]);
		hsv.saturation = Math.trunc(1.3 * hsv.saturation);
		if (hsv.value < 40) hsv.value *= 2;
	}
	hsv.hue += golden_ratio_conjugate;
	hsv.hue %= 1;
	rgb = HSV2RGB(hsv);

	if (logColor) console.log("colorBaseGolden: " + rgb.r + " " + rgb.g + " " + rgb.b);

	return [rgb.r, rgb.g, rgb.b ];
}

function colorBaseRand(rgbarr)
{

	//var radius = Math.trunc( rgbarr[3] / 1.5 );

	var r = Math.random() * 256;
	r = (r + rgbarr[0]) / 2;
	r = Math.trunc(r);

	var g = Math.trunc(Math.random() * 256);
	g = (g + rgbarr[1]) / 2;
	g = Math.trunc(g);

	var b = Math.trunc(Math.random() * 256);
	b = (b + rgbarr[2]) / 2;
	b = Math.trunc(b);

	if (logColor) console.log("colorBaseRand: Color rand mix " + r + " " + g + " " + b);

	return [ r, g, b ];
}

function colorBaseCompl(rgbarr)
{
	var radius = 0; //Math.trunc( rgbarr[3] / 1.5 );
	var ll = rgbarr[4];
	var contrast = rgbarr[5];

	var rgb = new Object();
	// rotate
	if (ll < 40) { // light
		rgb.r = (rgbarr[1] + radius) % 255;
		rgb.g = (rgbarr[2] + radius) % 255;
		rgb.b = (rgbarr[0] + radius) % 255;
	} else {
		rgb.r = (rgbarr[2] + radius) % 255;
		rgb.g = (rgbarr[0] + radius) % 255;
		rgb.b = (rgbarr[1] + radius) % 255;
	}
/*
	rgb.r = (rgbarr[0] + radius) % 255;
	rgb.g = (rgbarr[1] + radius) % 255;
	rgb.b = (rgbarr[2] + radius) % 255;
*/
	var hsv = RGB2HSV(rgb);
	hsv.hue = HueShift(hsv.hue,180.0);
	rgb = HSV2RGB(hsv);

	// avg ll = 38
	if (ll > 55) { // light
		if (contrast < 50) {
            if (logDots) console.log("CAP light/lowc");
			var cap = 120;
			var gap = 80;
			if (rgb.r > cap) rgb.r = cap -
						Math.floor((gap * rgb.r)/255);
			if (rgb.g > cap) rgb.g = cap +
						Math.floor((gap * rgb.g)/255);
			if (rgb.b > cap) rgb.b = cap +
						Math.floor((gap * rgb.b)/255);
		} else {
            if (logDots) console.log("CAP light/hic");
			var cap = 160;
			var gap = 255 - cap;
			if (rgb.r > cap) rgb.r = cap +
						Math.floor((gap * rgb.r)/255);
			if (rgb.g > cap) rgb.g = cap +
						Math.floor((gap * rgb.g)/255);
			if (rgb.b > cap) rgb.b = cap +
						Math.floor((gap * rgb.b)/255);
		}
	} else if (ll > 30) {
		if (contrast < 50) {
            if (logDots) console.log("CAP med/lowc");
			var cap = 190;
			var gap = 255 - cap;
			if (rgb.r < cap) rgb.r = cap +
						Math.floor((gap * rgb.r)/255);
			if (rgb.g < cap) rgb.g = cap +
						Math.floor((gap * rgb.g)/255);
			if (rgb.b < cap) rgb.b = cap +
						Math.floor((gap * rgb.g)/255);
		} else {
            if (logDots) console.log("CAP med/hic");
			var cap = 180;
			var gap = 255 - cap;
			if (rgb.r < cap) rgb.r = cap +
						Math.floor((gap * rgb.r)/255);
			if (rgb.g < cap) rgb.g = cap +
						Math.floor((gap * rgb.g)/255);
			if (rgb.b < cap) rgb.b = cap +
						Math.floor((gap * rgb.b)/255);
		}
	} else { // dark
		if (contrast < 50) {
            if (logDots) console.log("CAP dark/lowc");
			var cap = 80;
			var gap = 255 - cap;
			if (rgb.r < cap) rgb.r = cap +
						Math.floor((gap * rgb.r)/255);
			if (rgb.g < cap) rgb.g = cap +
						Math.floor((gap * rgb.g)/255);
			if (rgb.b < cap) rgb.b = cap +
						Math.floor((gap * rgb.b)/255);
		} else {
            if (logDots) console.log("CAP dark/hic");
			var cap = 180;
			var gap = 255 - cap;
			if (rgb.r < cap) rgb.r = cap +
						Math.floor((gap * rgb.r)/255);
			if (rgb.g < cap) rgb.g = cap +
						Math.floor((gap * rgb.g)/255);
			if (rgb.b < cap) rgb.b = cap +
						Math.floor((gap * rgb.b)/255);
		}
	}
	if (logColor) console.log("colorBaseCompl: " + rgb.r + " " + rgb.g + " " + rgb.b);

	return [rgb.r, rgb.g, rgb.b ];

}

//min max via Hairgami_Master (see comments)
function min3(a,b,c) {
	return (a<b)?((a<c)?a:c):((b<c)?b:c);
}
function max3(a,b,c) {
	return (a>b)?((a>c)?a:c):((b>c)?b:c);
}

function RGB2HSV(rgb) {
	var hsv = new Object();
	var max = max3(rgb.r,rgb.g,rgb.b);
	var dif = max - min3(rgb.r,rgb.g,rgb.b);
	hsv.saturation = (max==0.0)?0:(100*dif/max);
	if (hsv.saturation==0) hsv.hue=0;
	else if (rgb.r==max) hsv.hue=60.0*(rgb.g-rgb.b)/dif;
	else if (rgb.g==max) hsv.hue=120.0+60.0*(rgb.b-rgb.r)/dif;
	else if (rgb.b==max) hsv.hue=240.0+60.0*(rgb.r-rgb.g)/dif;
	if (hsv.hue<0.0) hsv.hue+=360.0;
	hsv.value=Math.round(max*100/255);
	hsv.hue=Math.round(hsv.hue);
	hsv.saturation=Math.round(hsv.saturation);
	return hsv;
}

// RGB2HSV and HSV2RGB are based on Color Match Remix [http://color.twysted.net/]
// which is based on or copied from ColorMatch 5K [http://colormatch.dk/]
function HSV2RGB(hsv) {
	var rgb=new Object();
	if (hsv.saturation==0) {
		rgb.r=rgb.g=rgb.b=Math.round(hsv.value*2.55);
	} else {
		hsv.hue/=60;
		hsv.saturation/=100;
		hsv.value/=100;
		i=Math.floor(hsv.hue);
		f=hsv.hue-i;
		p=hsv.value*(1-hsv.saturation);
		q=hsv.value*(1-hsv.saturation*f);
		t=hsv.value*(1-hsv.saturation*(1-f));
		switch(i) {
			case 0: rgb.r=hsv.value; rgb.g=t; rgb.b=p; break;
			case 1: rgb.r=q; rgb.g=hsv.value; rgb.b=p; break;
			case 2: rgb.r=p; rgb.g=hsv.value; rgb.b=t; break;
			case 3: rgb.r=p; rgb.g=q; rgb.b=hsv.value; break;
			case 4: rgb.r=t; rgb.g=p; rgb.b=hsv.value; break;
			default: rgb.r=hsv.value; rgb.g=p; rgb.b=q;
		}
		rgb.r=Math.round(rgb.r*255);
		rgb.g=Math.round(rgb.g*255);
		rgb.b=Math.round(rgb.b*255);
	}
	return rgb;
}

//Adding HueShift via Jacob (see comments)
function HueShift(h,s) {
	h+=s; while (h>=360.0) h-=360.0; while (h<0.0) h+=360.0; return h;
}

function wrapFac(prMeta, rgbval)
{
	if (dotList.length % 2) return Math.floor(rgbval);

    var partRadius = 200 * prMeta[3];
	var val = Math.max(160, Math.floor(partRadius + rgbval) % 255);
//console.log('RGBRAD partRadius ' + partRadius + '-> ' + val);
	return val;
/*
        var fac = 1.0 + prMeta[3];
console.log('RGBRAD frac ' + fac);
	return Math.max(140, Math.floor(fac * rgbval) % 255);
*/
}

function greyBase(dot, prMeta, day, hour, millis)
{
	var msg = "B:GREY|";

	var val = 255; // white
	if (dotList.length > 0) {
		var dt = dot.time - dotList[0].time;
		val = 144 + (dt * dt * dt) % 100; // 144==background
	}
	msg += 'bw' + val + '|';

/*
	var val = 144; // value of background
	if (dotList.length > 30) {
		val = 255;
	} else {
		val += Math.floor(1.7 * dotList.length);
	}
*/

	if (hour < 8  ||  hour > 18) { // 'night'
		if (hour < 6  &&  hour > 2) { // 'nightowl'
			msg += 'nightowl-5|';
			val -= 5;
		} else {
			msg += 'night|';
		}
	} else {
		msg += 'dayhr' + hour + '|';
	}

	if (day == 0  ||  day == 6) { // weekend
		msg += 'wknd+5|';
		val += 5;
	}

	if (millis % 2 == 0) {
		// saves screening others
	} else if (millis % 3 == 0) {
		val -= 1;
	} else if (millis % 5 == 0) {
		val -= 3;
	}
	val = wrapFac(prMeta, val);

    if (logDots) console.log('BW val ' + val + ' millis ' + millis);

	msg += 'bw' + val + '|';

	return [ "rgba(" + val + "," + val + "," + val + ",", msg ];
}

function greyTailBase(dot, prMeta, day, hour, millis)
{
	var complRGB = colorBaseArr(prMeta, dot.vel);

	r = complRGB[0];
	g = complRGB[1];
	b = complRGB[2];

	r = wrapFac(prMeta, r);
	g = wrapFac(prMeta, g);
	b = wrapFac(prMeta, b);

	return [ "rgba(" + r + "," + g + "," + b + ",", "B:GTB|" ];
}

function redBase(dot, prMeta, day, hour, millis)
{
	var msg = 'B:red|';

	// peak = ~red, modified by day of week and time
	// RGB red:    255,0,0
	// RGB orange: 255,90,10
	// RYB red:    254,39,18

	var val1 = 0;
	var val2 = 0;

	if (hour < 8  ||  hour > 18) { // 'night'
		msg += 'night|';
		if (hour < 6  &&  hour > 2) { // 'nightowl'
			msg += 'owl|';
			val1 = 90;
			val2 = 10;
		} else {
			val1 = 40;
			val2 = 15;
		}

		if (millis % 2 == 0) {
			// saves screening others
		} else if (millis % 3 == 0) {
			val1 -= 5;
		} else if (millis % 5 == 0) {
			val1 -= 10;
		} else if (millis % 7 == 0) {
			val1 -= 15;
		}
	} else { // 'day'
		msg += 'day|';
		if (hour > 11  &&  hour < 13) { // 'lunch'
			msg += 'lunch|';
			val1 += 25;
		} else if (hour < 11) {
			msg += 'am|';
			val1 += 18;
		} else {
			msg += 'pm|';
			val1 += 45;
		}
		if (millis % 2 == 0) {
			// screen
		} else if (millis % 3 == 0) {
			val1 += 2;
		} else if (millis % 5 == 0) {
			val2 = 25;
		}
	}

	val1 = wrapFac(prMeta, val1);
	val2 = wrapFac(prMeta, val2);

	return [ "rgba(254," + val1 + "," + val2 + ",", msg ];
}

function grnyelBase(dot, prMeta, day, hour, millis)
{
	var msg = 'B:grnyel|';

	// 55,255,55 = RGB green
	// 102,176,50 = RYB green

	// 255,255,0 = RGB yellow
	// 254,254,51 = RYB yellow

	// 178,215,50 = RYB y/g

	var val1 = 75;
	var val2 = 55;

	if (hour < 8  ||  hour > 18) { // 'night'
		msg += 'night|';
		if (hour < 6  &&  hour > 2) { // 'nightowl'
			msg += 'owl|';
			val1 += 100;
			val2 -= 20;
		} else {
			val1 += 5;
			val2 -= 3;
		}

		if (millis % 2 == 0) {
			// saves screening others
		} else if (millis % 3 == 0) {
			val1 += 7;
			val2 -= 3;
		} else if (millis % 4 == 0) {
			val1 += 12;
			val2 -= 5;
		} else if (millis % 5 == 0) {
			val1 += 17;
			val2 -= 8;
		}
	} else { // 'day'

		msg += 'day|';

		if (hour > 11  &&  hour < 13) { // 'lunch'
			msg += 'lunch|';
			//
		} else if (hour < 11) {
			msg += 'am|';
			val1 += 25;
			val2 -= 5;
		} else {
			msg += 'pm|';
			val1 += 8;
			val2 -= 3;
		}

		if (millis % 2 == 0) {
			// screen
		} else if (millis % 3 == 0) {
			val1 += 1;
			val2 -= 2;
		} else if (millis % 4 == 0) {
			val1 += 10;
			val2 -= 9;
		}
	}

	val1 = wrapFac(prMeta, val1);
	val2 = wrapFac(prMeta, val2);

	return [ "rgba(" + val1 + "," + "255," + val2 + ",", msg ];
}

function blueBase(dot, prMeta, day, hour, millis)
{
	var msg = 'B:blue|';

	// ~blue = 0, 191, 255 to 0, 64, 255

	var val1 = 0;
	var val2 = 120;
	var val3 = 255;

	if (hour < 8  ||  hour > 18) { // 'night'
		msg += 'night|';
		if (hour < 6  &&  hour > 2) { // 'nightowl'
			msg += 'owl|';
			val2 = 90;
		} else {
			val2 = 64;
		}
	} else {
		msg += 'day|';
		if (hour > 11  &&  hour < 13) { // 'lunch'
			msg += 'lunch|';
			val2 += 25;
		} else if (hour < 11) {
			msg += 'am|';
			val2 += 20;
		} else {
			msg += 'pm|';
			val2 += 15;
		}
	}
	if (millis % 2 == 0) {
		val1 += 15;
	} else if (millis % 3 == 0) {
		val2 -= 15;
		val3 -= 15;
	} else if (millis % 5 == 0) {
		val1 += 12;
		val2 -= 12;
		val3 -= 12;
	} else if (millis % 7 == 0) {
		val1 += 22;
		val2 -= 15;
		val3 -= 25;
	}

	val1 = wrapFac(prMeta, val1);
	val2 = wrapFac(prMeta, val2);
	val3 = wrapFac(prMeta, val3);

	return [ "rgba(" + val1 + "," + val2 + "," + val3 + ",", msg ];
}

function minChannel(prMeta)
{
	var min = 0;

	if (prMeta[1] < prMeta[0]) {
		if (prMeta[2] < prMeta[1]) {
			min = 2;
		} else {
			min = 1;
		}
	} else if (prMeta[2] < prMeta[0]) {
		min = 2;
	}
	if (min != 1  &&  2 * prMeta[1] < prMeta[0] + prMeta[2]) {
		if (logDots) console.log('HAH/min');
		min = 1;
	}
	return min;
}

function maxChannel(prMeta)
{
	var max = 0;
	if (prMeta[1] > prMeta[0]) {
		if (prMeta[2] > prMeta[1]) {
			max = 2;
		} else {
			max = 1;
		}
	} else if (prMeta[2] > prMeta[0]) {
		max = 2;
	}
	if (max != 1  &&  2 * prMeta[1] > prMeta[0] + prMeta[2]) {
		if (logDots) console.log('HAH/max');
		max = 1;
	}
	return max;
}

var spiralRGB = null;

function colorSpiralBase(dot, prMeta, day, hour, millis)
{
//var t0 = new Date();
//console.log('SPIRAL base');
	if (dotList.length == 0  ||  spiralRGB == null) {

		spiralRGB = colorBaseArr(prMeta, dot.vel);

		var min = minChannel(spiralRGB);
		spiralRGB[min] = Math.trunc(0.7 * spiralRGB[min]);
		var max = maxChannel(spiralRGB);
		spiralRGB[max] = Math.trunc(1.3 * spiralRGB[max]) % 255;
		spiralRGB.time = dot.time;
//console.log('SETUP ' + (new Date()-t0));
	}
	var dt = dot.time - spiralRGB.time;
	var sin = Math.sin(dt);
	var cos = Math.cos(dt);

//console.log('GEOM ' + (new Date()-t0));
	var r, g, b;
	if (dt % 2 == 0) {
		r = spiralRGB[0] + (0.5 * sin * spiralRGB[0]);
		g = spiralRGB[1] + (0.5 * cos * spiralRGB[1]);
		b = spiralRGB[2] + (0.5 * cos * spiralRGB[2]);
	} else if (dt % 3 == 0) {
		r = spiralRGB[0] + (0.5 * cos * spiralRGB[0]);
		g = spiralRGB[1] + (0.5 * cos * spiralRGB[1]);
		b = spiralRGB[2] + (0.5 * sin * spiralRGB[2]);
	} else {
		r = spiralRGB[0] + (0.5 * sin * spiralRGB[0]);
		g = spiralRGB[1] + (0.5 * sin * spiralRGB[1]);
		b = spiralRGB[2] + (0.5 * cos * spiralRGB[2]);
	}

//console.log('SPIRAL ' + (new Date()-t0));
	if (dot.accel > 0) {
		var fac = 0.5;
		if (dot.accel > 30) {
			fac = 0.33;
		}
		r = 255 - fac * r;
		g = 255 - fac * g;
		b = 255 - fac * b;
		//r *= fac; g *= fac; b *= fac;
	}

	r = wrapFac(prMeta, r);
	g = wrapFac(prMeta, g);
	b = wrapFac(prMeta, b);

	return [ "rgba(" + r + "," + g + "," + b + ",", 'B:spiral|' ];
}

function colorTailBase(dot, prMeta, day, hour, millis)
{

	if (dotList.length > 16) {

		return greyTailBase(dot, prMeta, day, hour, millis);

	}

	// color 'head'

	// pick min(r,g,b)
	var min = minChannel(prMeta);

/*
	// pick max(r,g,b)
	var max = 0;
	if (prMeta[1] > prMeta[0]) {
		if (prMeta[2] > prMeta[1]) {
			max = 2;
		} else {
			max = 1;
		}
	} else if (prMeta[2] > prMeta[0]) {
		max = 2;
	}
	if (max != 1  &&  2 * prMeta[1] > prMeta[0] + prMeta[2]) {
		console.log('HAH');
		max = 1;
	}
*/
	var res;
	switch (min) {
		case 0:
			res = redBase(dot, prMeta, day, hour, millis);
			break;
		case 1:
			res = grnyelBase(dot, prMeta, day, hour, millis);
			break;
		case 2:
			res = blueBase(dot, prMeta, day, hour, millis);
			break;
		default:
			alert('HUH ' + min);
	}
	//return [ res[0], 'HEAD' + max + '|' + res[1] ];
	return [ res[0], 'HEAD' + min + '|' + res[1] ];
}

function colorBase(dot, prMeta, day, hour, millis)
{
	var res = null;

	if (dot.accel > 0) {
		if (dot.vel > 90) {
			res = blueBase(dot, prMeta, day, hour, millis);
		} else {
			res = grnyelBase(dot, prMeta, day, hour, millis);
		}
	} else if (dot.vel > 50) {
		res = redBase(dot, prMeta, day, hour, millis);
	} else if (dot.vel < dot.size) {
		res = grnyelBase(dot, prMeta, day, hour, millis);
	} else if (dot.vel > 25) {
		res = blueBase(dot, prMeta, day, hour, millis);
	} else {

		var val;
		if (dotList.length % 2) {
			val = 255 - millis % 5;
		} else {
			val = millis % 4;
		}
		val = wrapFac(prMeta, val);

		res = [ "rgba(" + val + "," + val + "," + val + ",",
			'else=grey|' ];
	}

	return [ res[0], 'B:CB|' + res[1] ];
}

// dialogDotsBlocked and ratingAlerts are incremented in
//      this pkg, then reset by page after they go in the next request

var dialogDotsBlocked = 0;
var ratingAlerts = 0;

var dialogFlowRating = null;

function rateFlow(status)
{
    console.log("rateFlow " + dialogFlowRating + " -> " + status +
            "\n\t(dotsblocked " + dialogDotsBlocked +
            " ratingAlerts " + ratingAlerts + ")");

    dialogFlowRating = status;
}

function dialogFlowNeutral()
{
    console.log("flow  " + dialogFlowRating + " -> 0" +
            "\n\t(dotsblocked " + dialogDotsBlocked +
            " ratingAlerts " + ratingAlerts + ")");
    dialogFlowRating = 0;
}

// return null if error
function getFeeling()
{
  if (dialogFlowRating == null) {
    alert('Rating is null');
    return null;
  }

  console.log("getFeeling: " + dialogFlowRating);

  return dialogFlowRating;
}

// mouseDown is defined in-page

var skipped_dt = 0;
var skipped_dist = 0;

function placeDot(scrn, startsize, cutdistsq, x, y, now, mobile)
{
    if (!mouseDown  &&  !hasTouch) {
        // startsize == cutdistsq == -1, why even call?
        //  -- seems a broken carryover from multiple draws
        if (logDots) console.log("placeDot - skip on mouse down: " + mouseDown + " hasTouch: " + hasTouch);
        return;
    }

    // requireRating set true in html
    if (requireRating  &&  dialogFlowRating == null) {

        if (dialogDotsBlocked == 0) {
            console.log("placeDot: dialogFlowRating is null - must rate to dot");
        }

        dialogDotsBlocked++;

        if (dialogDotsBlocked == 111  ||  dialogDotsBlocked % 1111 == 0) {
            alert('Expecting rating before draw');
            mouseDown = false;  // acking the alert hides mouseDown reset
            ratingAlerts++;
        }

        return;
    }

	var t0 = new Date();

	if (dotList.length == 0) {
		dotSpeed = 1;
	}

	var dot = document.createElement('div');
	dot.className = "dot";
	dot.time = now;
    dot.dt = 0;
	dot.x = x;
	dot.y = y;
	dot.style.left = x + "px";
    dot.style.top = y + "px";

    if (startsize == -1  &&  cutdistsq == -1) {
        // mouseup from html, see commented-out
        // placeDot call there
        dot.mouseUp = true;
		dotList.push(dot);
		return;
    }

	if (cacheDots) {
		dotList.push(dot);
		if (logDots) console.log('cacheDots: N ' + dotList.length);
		return;
	}

	dot.dist = 0;
	dot.vel = 0;
	dot.accel = 0;

	var prevDot = null;
	var pprevDot = null;
	var d3angle = null;
	var d2angle = null;

	var msg = "";
	var facmsg = "";

	if (dotList.length > 0) {
		prevDot = dotList[dotList.length-1];
        while (prevDot != null  &&  prevDot.size == 0) {
            console.log("prevDot.size is 0??? popping, dots: " + dotList.length);
            dotList.pop();
            if (dotList.length == 0) {
                prevDot = null;
            }
        }
    }
	if (prevDot != null) {

		var dt = dot.time - prevDot.time;
		if (dt < 10) {
            skipped_dt++;
			if (logSkips) console.log('SKIP dt ' + dt);
			return;
		}
        dot.dt = dt;

		var dx = dot.x - prevDot.x;
		var dy = dot.y - prevDot.y;

		var distsq = dx*dx + dy*dy;

		if (distsq < cutdistsq) {
            skipped_dist++;
			if (logSkips) console.log('d2='+distsq+' < cut=' + cutdistsq +
				' / skip dot, dt=' + dt);
			return;
		}

		dot.dist = Math.sqrt(distsq); // sqrt/vel/accel at end?

		if (dot.dist < startsize) {
			if (dotList.length % 2) {
				if (logSkips) console.log('d < ss + even: skip dot, dt=' + dt);
				return;
			}
			msg += '[d<ss]|';
		}

		dot.vel = (100 * dot.dist) / dt;
		dot.accel = dot.vel - prevDot.vel;
        dot.jerk = Math.abs(dot.accel) - Math.abs(prevDot.accel);

        var d2angle = vecAngle( dy, dx );
		var histBin = Math.abs(Math.floor(d2angle/10.0));
        //console.log('d2a ' + d2angle + ' bin ' + histBin);
        d2angleHist[histBin]++;

		if (dotList.length > 1) {
			pprevDot = dotList[dotList.length-2];
			d3angle = Math.atan2(dot.y-prevDot.y,
						dot.x-prevDot.x) -
				Math.atan2(pprevDot.y-prevDot.y,
						pprevDot.x-prevDot.x);
			d3angle = (d3angle * 180) / Math.PI;
            while (d3angle < 0) d3angle += 360;
			histBin = Math.floor(d3angle/10.0);
//console.log("d3 ANG " + d3angle + "  BIN " + histBin);
			d3angleHist[histBin]++;
		}
	}

	// dot size mumbo jumbo

	var size = startsize;

	if (size > dot.dist) {

		msg += 'sz:d<ss|';
		facmsg = null;

        size = Math.floor( (dot.dist * dot.dist) / size);

        if (size > dot.dist) {
		    if (millis % 2 == 0) {
			    size = 0.8 * dot.dist;
		    } else if (millis % 3 == 0) {
			    size = 0.7 * dot.dist;
		    } else if (millis % 5 == 0) {
			    size = 0.6 * dot.dist;
		    } else {
			    size = 0.5 * dot.dist;
		    }
        }
	}

    // velocity effect
    //  25/7/28 vel in ~100-300
    var vel_fac = 1.0;
    if (dot.vel > 300) {
	    msg += 'v>300|';
	    vel_fac = 3;
	} else if (dot.vel > 200) {
		msg += 'v>200|';
		vel_fac = 2;
	} else if (dot.vel > 100) {
		msg += 'v>100|';
		vel_fac = 1.5;
	} else if (dot.vel > 50) {
		msg += 'v>50|';
		vel_fac = 3;
	} else  {
		msg += 'v<=50|';
		vel_fac = 5;
    }
    if (dot.accel > 0  &&  dot.jerk > 0) {
        msg += 'a>j>|';
        vel_fac *= 1.5;
    } else if (dot.accel > 0  &&  dot.jerk <= 0) {
        msg += 'a>j<|';
        vel_fac *= 0.75;
    } else if (dot.accel <= 0  &&  dot.jerk > 0) {
        msg += 'a<j>|';
        vel_fac *= 0.25;
    } else /* if (dot.accel <= 0  &&  dot.jerk <= 0) */ {
        msg += 'a<j<|';
        vel_fac *= 1.75;
    }

    size *= vel_fac;

    // adjust

	if (size > dot.dist) {
		msg += 'sz'+Math.ceil(size)+'>dist';
        size = Math.ceil((dot.dist * dot.dist)/size);
        msg += '=>' + size + '|';
	}
	if (size < 3) {
		size = 3;
		msg += 'sz=>3';
	} else if (hasTouch) {
/*
		if (size > 45) {
			if (size > 60  &&  Math.random() < 0.3) {
				msg += 'sz=>60';
				size = 60;
			} else {
				msg += 'sz=>45';
				size = 45;
			}
		}
*/
	}

	if (prevDot != null  &&  size > 3 * prevDot.size) {
		msg += '>3*prev|';
		size = 2.8 * prevDot.size;
	}

	dot.size = Math.ceil(size);
	var dim = dot.size + 'px';
	msg += '=>' + dim +
            '| skipped dt/d ' +
            skipped_dt + '/' +
            skipped_dist + '\n';

    skipped_dt = 0;
    skipped_dist = 0;

    if (logDots) console.log("placeDot @" + x + "," + y +
                                " startsize " + startsize + " cutsq " + cutdistsq +
                                " dotlist sz " + dotList.length +
                                // " mouseDown is " + mouseDown + " mobile is " + mobile +
                                ( requireRating ? " rating " + dialogFlowRating : "") +
                                "\n  size " + dot.size + ": " + msg +
                                "  dt " + dot.dt +
                                " dist " + Math.ceil(dot.dist) +
                                " vel " + Math.ceil(dot.vel) +
                                " accel " + Math.ceil(dot.accel) +
                                " jerk " + Math.ceil(dot.jerk) +
                                " vel_fac " + vel_fac);

	dot.style.width = dim;
	dot.style.height = dim;

//console.log('DIM ' + dim + ' dist ' + dot.dist + ' vel ' + dot.vel + ' t ' + (new Date()-t0));
//dot.style.backgroundColor = "red";
//document.body.appendChild(dot);
//dotList.push(dot);
//return;

	var day = now.getDay();
	var hour = now.getHours();
	var millis = now.getMilliseconds();

	var key = (scrn % 3 == 0 ? "2." : "1.") + (1+vDepth[scrn])%2;
//console.log('KEY '  + key);
	var prMeta = rgbs[key];

	var res; // [base, basemsg]
	//res = colorSpiralBase(dot, prMeta, day, hour, millis);

	if (!colorDraw) {

		res = greyBase(dot, prMeta, day, hour, millis);

	} else {
        //if (logDots) console.log('============ colorBase, prm ');// + prMeta);
		var dotStyle = prMeta[6];
		switch(dotStyle) {
			case 0:
				res = colorSpiralBase(dot, prMeta,
							day, hour, millis);
				break;
			case 1:
				if (millis % 2 == 0   ||  millis % 3 == 0) {
					res = colorTailBase(dot, prMeta,
							day, hour, millis);
				} else {
					res = colorSpiralBase(dot, prMeta,
							day, hour, millis);
				}
				break;
			case 2:
				if (millis % 5 == 0) {
					res = colorSpiralBase(dot, prMeta,
							day, hour, millis);
				} else {
					res = colorBase(dot, prMeta,
							day, hour, millis);
				}
				break;
			case 3:
				if (millis % 2 == 0) {
					res = colorTailBase(dot, prMeta,
							day, hour, millis);
				} else if (millis % 3 == 0) {
					res = colorSpiralBase(dot, prMeta,
							day, hour, millis);
				} else {
					res = colorBase(dot, prMeta,
							day, hour, millis);
				}
				break;
			case 4:
				res = colorSpiralBase(dot, prMeta,
							day, hour, millis);
				break;
			case 5:
			default:
				res = colorBase(dot, prMeta,
							day, hour, millis);
				break;
		}
		res[1] = 'cstyle:' + dotStyle + '|' + res[1];
	}

	var baseColor = res[0];
	msg += res[1];

	// solid/transparency

	if (dotList.length == 0) {
		dot.solid = 0.7;
	} else if (dotList.length < 9) {
		dot.solid = 0.2 + (0.1 * dotList.length);
	} else {
		var reversible = true;
		if (solidIncrease) {
			if (prevDot.solid > 0.9) {
//console.log('solid incr FLIP');
				msg += 'sol->down|';
				solidIncrease = !solidIncrease;
				dot.solid = prevDot.solid - 0.15;
				reversible = false;
			} else {
				msg += 'sol.inc|';
				dot.solid = prevDot.solid + 0.1;
				if (dot.solid < 0.7) {
					reversible = false;
				}
			}
		} else {
			if (prevDot.solid < 0.5) {
				msg += 'sol->up|';
//console.log('solid incr FLIP');
				solidIncrease = !solidIncrease;
				dot.solid = prevDot.solid + 0.15;
				reversible = false;
			} else {
				msg += 'sol.dec|';
				dot.solid = prevDot.solid - 0.1;
				if (dot.solid > 0.6) {
					reversible = false;
				}
			}
		}
		if (reversible) {
            if (d2angle != null  &&
			    (d2angle < 160 || d2angle > 200)) {
				    msg += 'sol/d2ang/flip|';
//console.log("solid d2 ANG FLIP " + d2angle);
		        solidIncrease != solidIncrease;
		    } else if (d3angle != null  &&
                (d3angle < 160 || d3angle > 200)) {
//console.log("solid d3 ANG FLIP " + d3angle);
		        solidIncrease != solidIncrease;
		    }
        }
/*
		var cut1 = 0.2, cut2 = 0.65, f1 = -0.1, f2 = 1;
		if (mobile) {
			cut1 = 5; cut2 = 18; f1 = 10; f2 = 5;
		}
		if (dot.vel < cut1) {
			solid = 0.6;
		} else if (dot.vel > cut2) {
			solid = 0.8;
		} else {
			solid = (dot.vel - f1) / f2;
            if (logDots) console.log('sv ' + solid + ' ' + dot.vel + ' (' + f1 + ' / ' + f2 + ')');
		}
*/
	}

/*
	solid += 0.1 * (millis % 5) / 7;

	if (hour < 8  ||  hour > 18) { // 'night'
		if (hour < 6  &&  hour > 2) { // 'nightowl'
			solid -= 0.1;
		} else {
			solid -= 0.05;
		}
		if (solid > 0.9) {
			if (logDots) console.log('***** ** ** trim solid ' + solid);
			solid = 0.9;
		}
	} else {
		if (solid < 0.7) {
			if (!hasTouch) solid += 0.2;
		} else if (solid > 0.91) {
			if (logDots) console.log('***** ** ** trim solid ' + solid);
			solid = 0.91;
		}
	}
*/
	dot.solid = Math.round(dot.solid * 100) / 100;
    if (isNaN(dot.solid)) {
		dot.solid = 0.2 + (0.1 * dotList.length);
    }

	var colstr = baseColor + dot.solid + ")";
//console.log('DOT ' + prMeta + '\n' + msg + '\n=> ' + colstr);
	dot.style.backgroundColor = colstr;

	//var t1 = new Date()-t0;
	document.body.appendChild(dot);
	dotList.push(dot);

	var gap = 0;
	if (dotList.length > 1) {
		gap = dot.time - dotList[dotList.length-2].time;
	}

    if (gap > 700) {
		if (gap > 1400  ||  Math.random() < 0.5) {
			var tmp = Math.floor(Math.random() * 6);
			if (logDots) console.log('GAP->NEW TYPE ' +
				prMeta[6] + ' -> ' + tmp);
			prMeta[6] = tmp;
		}
    }
	if (dotList.length == 5) {
		startDotGibber(true); // 'slow'
	}
//console.log('dot ' + dotList.length +
//' color ' + colstr +
//'\nv ' + dot.vel +
//'\nt ' + t1 + ' ' + (new Date()-t0));
}


function overlap(o1, o2)
{
	var rect1 = o1.getBoundingClientRect();
	var rect2 = o2.getBoundingClientRect();
	return !( dotRect.right < imgRect.left ||
				dotRect.left > imgRect.right  ||
				dotRect.bottom < imgRect.top  ||
				dotRect.top > imgRect.bottom);
}

var drawTouch = function(img, scrn)
{
	if (!hasTouch) return;

	var finish = function (e) {

//alert('end ' + inPic + ' dots: ' + dotList.length);
		if (dotList.length == 0) {
			console.log('End/ empty dotList/2fingers?');
			return;
		}
		if (waitingDisplays != 0) {
			console.log('end: waitingDisplays: ' + waitingDisplays);
			return;
		}
//alert('end ' + e.touches.length);
		if (cmdMode != 1) {
			imgClick(scrn, e);
			return;
		}
		if (inPic != -1) {
			iClick(scrn, inPic, 'end');
			return;
		}

		// end not in either pic, so just go for
		// which pic's 'side' it's on (image1-4)

		var dot = dotList[dotList.length-1];
		var endSide = dotSide(dot);;
		iClick(scrn, endSide, 'end2');
	}
	var start = function(e) {
       	if (watchDots  &&  waitingDotty > 0) {
           	waitingDotty = 0;
			watchStart = null;
			finish(e);
			return;
		}

		if (e.touches.length > 1) {

//alert('touches>1: ' + e.touches.length + ' dotlist ' + dotList.length);
			if (dotList.length == 0) return;
if (dotList.length > 3) console.log('tch>1: ' + e.touches.length + ' dots: ' + dotList.length);

			reDot();
			return;
		}

//alert('sar [' + e.touches.length + ']');

		//ctx.beginPath();
		x = e.changedTouches[0].pageX;
		y = e.changedTouches[0].pageY;//-44;
		mousedownX = x;
		mousedownY = y;
		//ctx.moveTo(x,y);
		var now = updateXY(x, y, 'start');
		dot(x, y, now);
	};

	var end = function(e) {
//alert('end');
        	if (watchDots) {
                	waitingDotty++;
                	watchStart = new Date();
                	return;
		}
		finish(e);
	};
	var move = function(e) {

//alert('move');
		if (e.touches.length > 1) return;

//alert('mv ' + e.touches.length);
		e.preventDefault();
		var x = e.changedTouches[0].pageX;
		var y = e.changedTouches[0].pageY;//-44;

		var now = updateXY(x, y, 'move');
		dot(x, y, now);

		//ctx.lineTo(x,y);
		//ctx.stroke();
		//if (lastX != null) {
		//	document.body.appendChild(createLine(lastX, pY, x, y));
		//}

	};

    var dot = function(x, y, now) {

		//if (!inPic) return;
		// TODO calc if in a pic
		var size = 16 + Math.round((img.offsetWidth * img.offsetHeight)/ 80000);
		placeDot(scrn, size, size*size, x, y, now, true);
	};

	img.addEventListener("touchstart", start, false);
	img.addEventListener("touchend", end, false);
	img.addEventListener("touchmove", move, false);
};


(function() {
  //document.onmousemove = handleMouseMove;
  document.addEventListener('mousemove', handleMouseMove, false);

  function handleMouseMove(event) {

    var eventDoc, doc, body, pageX, pageY;
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
    var now = updateXY(event.pageX, event.pageY, 'move.event');
    if (inImage) {
//console.log('IN ' + mouseDown);

      if (mouseDown) {
        var dotSize = Math.ceil(30 +
            (window.innerHeight * window.innerWidth) / 800000);
	    placeDot(imageIn, dotSize, dotSize*dotSize, lastX, lastY, now, false);
      }
    }
//else console.log('NOT IN');
  }
})();


function resetDrawCounts()
{
  pixInPic = 0;
  pixOutPic = 0;
  dTot = 0;
  maxVel = 0;
  lastVel = 0;
  distMaxVel = 0;
  timeMaxVel = 0;
  maxAcc = 0;
  minAcc = 0;
  lastAcc = 0;
  maxJerk = 0;

  lastX = null;
  lastY = null;
  prevX = null;
  prevY = null;
  prevT = null;
/*
  mousedownX = null;
  mousedownY = null;
*/
  maxX = -1;
  minX = -1;
  maxY = -1;
  minY = -1;
  accumX = 0;
  accumY = 0;
  accumCount = 0;
}

function summarizeDots(logit)
{
	if (!logit) {
		if (logDots) console.log("DOTS n/a");
		return "&dc=0&dd=0&dv=0&da=0&dmv=0&dma=0&dmj=0&dh=0&ctm=-1" +
                "&ddb=" + dialogDotsBlocked +
                "&raa=" + ratingAlerts;
	}
	var dotCount = dotList.length;

	if (dotCount == 0) {
		if (logDots) console.log("NO DOTS");
		return "&dc=0&dd=0&dv=0&da=0&dmv=0&dma=0&dmj=0&dh=0&ctm=-1" +
                "&ddb=" + dialogDotsBlocked +
                "&raa=" + ratingAlerts;
	}

	// get pic coords

	var img1, img2;
	if (tileId == 1  ||  tileId == 2) {
		img1  = document.getElementById("image1");
		img2 = document.getElementById("image2");
	} else if (tileId == 3) {
		img1  = document.getElementById("image3");
		img2 = document.getElementById("image4");
	} else {
		alert('Unhandled tileId ' + tileId);
		img1  = document.getElementById("image1");
		img2 = document.getElementById("image2");
	}
    var rect1 = img1.getBoundingClientRect();
    var rect2 = img2.getBoundingClientRect();

	var dh = [];

	dh.push(-1 * d2angleHist.length);
    Array.prototype.push.apply(dh, d2angleHist);

	dh.push(-1 * d3angleHist.length);
    Array.prototype.push.apply(dh, d3angleHist);

    // distance is cutoff at hist size (added to 0'th),
    // velocity is normalized to hist size

    var H_SZ = 51;

    var distHist = new Array(H_SZ).fill(0);
    //var dmax = 0;
    var vmax = 0;
    for (var i=0; i<dotCount; i++) {
      var dot = dotList[i];
      if (dot.vel > vmax) vmax = dot.vel;
      var d = Math.round(dot.dist);
      //if (d > dmax) dmax = d;
      if (d > 0 && d < H_SZ) distHist[d]++;
      else if (d != 0  &&  !Number.isNaN(d)) {
        if (logHist) console.log('distHist outlier ' + d);
        distHist[0]++;
      }
    }
    if (distHist[0] > 0) console.log('distHist outliers ' + distHist[0]);
	dh.push(-1 * distHist.length);
    Array.prototype.push.apply(dh, distHist);

    var velHist = new Array(H_SZ).fill(0);
    for (var i=0; i<dotCount; i++) {
      var dot = dotList[i];
      var v = dot.vel;
      if (v != 0  &&  !Number.isNaN(v)) {
        v = Math.round(H_SZ * v / vmax) - 1;
        velHist[v]++;
      }
    }
	dh.push(-1 * velHist.length);
    Array.prototype.push.apply(dh, velHist);

    dh.push(-8);

    //alert('dists: ' + dists + '\n dmax ' + dmax);

	dh.push(Math.round(rect1.left));
    dh.push(Math.round(rect1.right));
	dh.push(Math.round(rect1.top));
    dh.push(Math.round(rect1.bottom));
	dh.push(Math.round(rect2.left));
    dh.push(Math.round(rect2.right));
	dh.push(Math.round(rect2.top));
    dh.push(Math.round(rect2.bottom));

	var dotDist = 0;
	var dotVectorLength = 0;
	var dotVectorAngle = 0;
	var dotMaxVel = 0;
	var dotMaxAccel = 0;
	var dotMaxJerk = 0;

	var dots_t0 = dotList[0].time;

    dh.push(-1 * dotCount * 3);
	//var rm = !cacheDots;
	for (var i=0; i<dotCount; i++) {
		var dot = dotList[i];
		//if (rm) {
		//	document.body.removeChild(dotList[i]);
		//}
		dh.push(Math.round(dot.x));
		dh.push(Math.round(dot.y));
        // use negative time to flag mouseUp
        var dt = dot.time - dots_t0;
        if (dot.mouseUp) dt *= -1;
		dh.push(dt);

        if (dot.mouseUp) {
            continue;
        }

        // calcs for mouse-down
		dotDist += dot.dist;
		var t = dot.vel;
		if (t > dotMaxVel) {
		    dotMaxVel = t;
		}
		t = dot.accel;
		if (t > dotMaxAccel) {
		    dotMaxAccel = t;
		}
		t = dot.jerk;
		if (t > dotMaxJerk) {
		    dotMaxJerk = t;
		}
	}

	var startDot = dotList[0];
	var endDot = dotList[dotCount-1];
//zQQQ
	var dX = endDot.x - startDot.x;
	var dY = endDot.y - startDot.y;

	dotVectorAngle = vecAngle( dY, dX );

	dotVectorLength = Math.sqrt(dX * dX + dY * dY);

	var clickTime = 0;
	if (dotList.length > 1) {
		clickTime = dotList[dotList.length-1].time - dotList[0].time;
	}

    // prepends int array size
	var dotHistory = decStr(dh);

    dotDist = Math.round(dotDist);
    dotVectorLength = Math.round(dotVectorLength);
    dotVectorAngle = Math.round(dotVectorAngle);
    dotMaxVel = Math.round(dotMaxVel);
    dotMaxAccel = Math.round(dotMaxAccel);
    dotMaxJerk = Math.round(dotMaxJerk);

	var params =
		"&ctm=" + clickTime +  // time mouse was down
		"&dc=" + dotCount +
		"&dd=" + dotDist +
		"&dv=" + dotVectorLength +
		"&da=" + dotVectorAngle +
		"&dmv=" + dotMaxVel +
		"&dma=" + dotMaxAccel +
		"&dmj=" + dotMaxJerk +
        "&ddb=" + dialogDotsBlocked +
        "&raa=" + ratingAlerts +
		"&dh=" + dotHistory;

	if (logit) console.log(
		"-- DOTS\nctm " + clickTime +
        " dc " + dotCount +
		" da/angle " + dotVectorAngle +
		" dv/vec_l " + dotVectorLength +
		" dd/dist " + dotDist +
		" dmv " + dotMaxVel +
		" dma " + dotMaxAccel +
		" dmj " + dotMaxJerk +
		//" dh " + dotHistory +
		" XY " + dX + "/" + dY +
		"\nANGLES.2dot " + d2angleHist + " sum " +
            d2angleHist.reduce((accumulator, currentValue) => accumulator + currentValue, 0) +
        "\nANGLES.3dot " + d3angleHist + " sum " +
            d3angleHist.reduce((accumulator, currentValue) => accumulator + currentValue, 0));

	return params;
}
