// abstract super class for the event responders
// removed in TrigFunc: var <>kind = \trig;		// \downup or \trig.
SSAbstractFunc {
	classvar ccs;
	classvar all;

	var <function;
	var <button;
	var <ledObj;
	var <responders;
	var <vals;
	var <>thresh=2;	// at least 2 is needed to protect from ghost events coming from the unit
	var <>state = 0;
	var <active = true;

	*new {|function, button, led=false|
		if (button.isInteger.not or: {button > 13}) {"arg button must be an integer from 0 to 13".inform; ^nil};
		if (SoftStep.initialized.not) {"Run SoftStep.initialize first.".inform; ^nil};
		^super.newCopyArgs(function, button).init(led);
	}

	init {arg led;
		vals = Array.fill(ccs[button].size, {0});
		if (led and: {button < 10}) {
			ledObj = SoftStepLed(button);
			this.addDependant(ledObj);
		};
		this.initChild;
		all.add(this);
	}

	free {
		responders.do (_.free);
		this.tryPerform(\stopClock);
		this.removeDependant(ledObj);
	}

	active_ {arg flag;
		if (flag) {
			responders.do(_.enable);
		} {
			responders.do(_.disable);
		};
		active = flag;
	}

	remove {
		this.free;
	}

	// doesn't remove the freed objects from the List
	*removeAll {
		all.do (_.free);
	}

	*initClass {
		ccs = [[44,45,46,47], [52,53,54,55], [60,61,62,63], [68,69,70,71], [76,77,78,79],
			[40,41,42,43], [48,49,50,51], [56,57,58,59], [64,65,66,67], [72,73,74,75],
			[80], [81], [82], [83]];
		all = List.new;
	}
}

// the function gets evaluated with the raw input pressure values
// max output val is about 250
SSRawFunc : SSAbstractFunc {

	initChild {
		ccs[button].do {|i|
			responders = responders.add(
				MIDIFunc.cc({|val,num,chan,src|
					var corn = ccs[button].indexOf(num);
					vals[corn] = val;
					function.value(vals.sum);
					if (vals.sum == 0) {
						state = 0;
						this.changed(\state, state);
					} {
						if (state == 0 and:{vals.sum >= thresh}) {
							state = 1;
							this.changed(\state, state);
						};
					};
				}, i, nil, SoftStep.in.uid)
			);
		};
	}
}

// args function, button. The function gets evaluted on each down and up event with args 0 or 1
SSTrigFunc : SSAbstractFunc {

	initChild {
		ccs[button].do {|i|
			responders = responders.add(
				MIDIFunc.cc({|val,num,chan,src|
					var corn = ccs[button].indexOf(num);
					vals[corn] = val;
					if (vals.sum == 0) {
						state = 0;
						function.value(state);
						this.changed(\state, state);
					} {
						if (state == 0 and:{vals.sum >= thresh}) {
							state = 1;
							function.value(state);
							this.changed(\state, state);
						};
					};
				}, i, nil, SoftStep.in.uid)
			);
		};
	}

}

// toggles between 1 and 0 on each down event
SSToggleFunc : SSAbstractFunc {
	var pressed = 0;	// state var is needed for the toggle vals

	initChild {
		ccs[button].do {|i|
			responders = responders.add(
				MIDIFunc.cc({|val,num,chan,src|
					var corn = ccs[button].indexOf(num);
					vals[corn] = val;
					if (vals.sum == 0) {
						pressed = 0;
					} {
						if (pressed == 0 and:{vals.sum >= thresh}) {
							pressed = 1;
							state = 1 - state;
							function.value(state);
							this.changed(\state, state);
						};
					};
				}, i, nil, SoftStep.in.uid)
			);
		};
	}
}

// calls function if button is pressed for tm seconds. Default:1.2
SSLongTrigFunc : SSAbstractFunc {
	var rout;
	var playing=false;
	var <>tm=1.2;			// wait time till function call

	makeRout {
		playing = true;
		this.changed(\blink, state);
		rout = Routine({
			tm.wait;
			state = 1;
			function.value(state);
			this.changed(\state, state);
		}).play(SystemClock);
	}

	initChild {
		state = 1;
		ccs[button].do {|i|
			responders = responders.add(
				MIDIFunc.cc({|val,num,chan,src|
					var corn;
					corn = ccs[button].indexOf(num);
					vals[corn] = val;
					if (playing.not) {
						this.makeRout;
					} {
						if (vals.sum == 0) {
							playing = false;
							rout.stop;
							state = 0;
							this.changed(\state, state);
						}
					};
				}, i, nil, SoftStep.in.uid);
			)
		};
	}
}

SSDoubleTrigFunc : SSAbstractFunc {
	var rout;
	var playing=false;
	var <>tm=0.4;
	var click=0;  // set to 1 after first click. Needed to check whether this is the second click

	makeRout {
		playing = true;
		rout = Routine({
			tm.wait;
			playing = false;
			click = 0;
			this.changed(\state, 0);
		}).play(SystemClock)
	}

	initChild {
		state = 1;
		ccs[button].do {|i|
			responders = responders.add(
				MIDIFunc.cc({|val,num,chan,src|
					var corn;
					corn = ccs[button].indexOf(num);
					vals[corn] = val;
					if (playing.not) {
						this.makeRout;
					} {
						if (vals.sum == 0) {
							click = 1;
						}
					};
					if (vals.sum > 0 and: {playing} and: {click == 1}) {
						function.value(state);
						this.changed(\state, 1);
						playing = false;
						click = 0;
					};
				}, i, nil, SoftStep.in.uid);
			)
		};
	}
}

// calls the function with an arg counting up when upper half of the button is presed
// and vice versa. Speed of function calls is determined by foot pressure
SSYFunc : SSAbstractFunc {
	var <rout;
	var <playing=false;
	var <>step=0.005;		// size of steps the routine counts up and down
	var <>clipLo=0;		// lower range limit
	var <>clipHi=1;		// upper range limit
	var clock;			// TempoClock for the routine

	initChild {
		if (button > 9) {Error("This class only supports buttons from 0 to 9").throw};
		clock = TempoClock(1);
		ccs[button].do {|i|
			responders = responders.add(
				MIDIFunc.cc({|val,num,chan,src|
					var corn, su;
					corn = ccs[button].indexOf(num);
					vals[corn] = val;
					su = vals.sum;

					if (playing.not) {
						this.makeRout;
						this.changed(\state, 1);
					};
					if (su == 0) {
						playing = false;
						rout.stop;
						this.changed(\state, 0);
					};
					if (corn < 2) {
						if (step.isNegative) {
							step = step.neg
						}
					} {
						if (step.isPositive) {
							step = step.neg
						}
					};
					clock.tempo_(su.linexp(thresh, 200, 1, 100)); // set routine tempo
				}, i, nil, SoftStep.in.uid);
			);
		};
	}

	makeRout {
		playing = true;
		rout = Routine({
			inf.do {
				state = (state + step).clip(clipLo, clipHi);
				if (state >= clipLo and: {state <= clipHi}) {
					function.value(state);
					this.changed(\param, state);
				};
				1.wait;
			};
		}).play(clock);
	}

	// private method called by free/remove in the superclass
	stopClock {
		clock.stop;
	}
}

// calls the function with an arg counting up when right half of the button is presed
// and vice versa. Speed of function calls is determined by foot pressure
SSXFunc : SSYFunc {

	initChild {
		if (button > 9) {Error("This class only supports buttons from 0 to 9").throw};
		clock = TempoClock(1);
		ccs[button].do {|i|
			responders = responders.add(
				MIDIFunc.cc({|val,num,chan,src|
					var corn, su;
					corn = ccs[button].indexOf(num);
					vals[corn] = val;
					su = vals.sum;
					if (playing.not) {
						this.makeRout;
						this.changed(\state, 1);
					} {
						if (su == 0) {
							playing = false;
							rout.stop;
							this.changed(\state, 0);
						}
					};
					if (corn == 1 or:{corn == 3}) {
						if (step.isNegative) {
							step = step.neg;
						}
					} {
						if (step.isPositive) {
							step = step.neg;
						}
					};
					clock.tempo_(su.linexp(thresh, 200, 1, 100)); // set routine tempo
				}, i, nil, SoftStep.in.uid);
			);
		};
	}
}
/*
//Trig, Long Trig and Double Click Trig classes only for the SoftStep in Standalone mode
SSAbstractSimpleFunc {
	classvar all;

	var <function;
	var <button;
	var <ledObj;
	var <responders;
	var <>state = 0;
	var <active = true;

	*new {|function, button, led=false|
		if (button.isInteger.not or: {button > 13}) {"arg button must be an integer from 0 to 13".inform; ^nil};
		if (SoftStep.initialized.not) {"Run SoftStep.initialize first.".inform; ^nil};
		^super.newCopyArgs(function, button).init(led);
	}

	init {arg led;
		if (led and: {button < 10}) {
			ledObj = SoftStepLed(button);
			this.addDependant(ledObj);
		};
		this.initChild;
		all.add(this);
	}

	free {
		responders.do (_.free);
		this.tryPerform(\stopClock);
		this.removeDependant(ledObj);
	}

	active_ {arg flag;
		if (flag) {
			responders.do(_.enable);
		} {
			responders.do(_.disable);
		};
		active = flag;
	}

	remove {
		this.free;
	}

	// doesn't remove the freed objects from the List
	*removeAll {
		all.do (_.free);
	}
}

SSSimpleTrigFunc : SSAbstractSimpleFunc {
	initChild {
		responders.add(
			MIDIFunc.noteOn({

			}, 60+button);
		)
	}
}
*/