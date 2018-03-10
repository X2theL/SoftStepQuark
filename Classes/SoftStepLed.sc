SoftStepLed {
	classvar colors;

	var <button;  // number from 0-9 representing the button on the SS
	var <active;  // feature from SoftStepLedGroup
	var <state=0; // 0=off, 1=on, 2=blink slowly, 3=blink fast
	var <color=0; // 0=green, 1=red

	*new {arg button=0, active=true;
		if (SoftStep.initialized.not) {Error.throw("SoftStep not initialized.")};
		^super.newCopyArgs(button, active);
	}

	// set state and color. The sendMidi arg is var active now. 06.12.13!!! It break SoftStepLedGroup but atm I don't care!
	set {arg st=0, clr=0;
		color = clr;
		if (active) {
			state = st;
			this.updateLed;
		};
		state = st;
	}

	// send led to saved state and color
	updateLed {
			this.off;
			SoftStep.out.control(3, colors[color][button], state);
	}

	active_ {arg act;
		active = act;
		if (active.not) {
			if (state != 0) {this.off};
		} {
			this.updateLed;
		}
	}

	// clears led without changing state
	off {
		2.do {arg i; SoftStep.out.control(3, colors[i][button], 0)};
	}

	// SSFuncs can add this as a Dependant
	update {arg changer, what ...args;
		switch (what,
			\state, {this.set(args[0], 0)},
			\blink, {this.set(3, 0)},
			\vari, {this.set(1, args[0])}
		);
		//[what, args].postln;
	}

	*initClass {
		// colors[0] is green, colors[1] is red
		colors = [[110, 111, 112, 113, 114, 115, 116, 117, 118, 119],
				[20, 21, 22, 23, 24, 25, 26, 27, 28, 29]];
	}
}

// a group can be all 10 or the top or bottom 5 leds
SoftStepLedGroup {

	var <>name;
	var <active; // if set to false no midi is sent but states and colors are updated
	var <leds;

	*new {arg name, active=true, range=\whole; // \whole, \top, \bottom
		^super.newCopyArgs(name, active).init(range);
	}

	init {arg range;
		switch(range,
			\whole, {leds = 10.collect {arg i; SoftStepLed(i)}},
			\top, {leds = 5.collect {arg i; SoftStepLed(i + 5)}},
			\bottom, {leds = 5.collect {arg i; SoftStepLed(i)}}
		);
	}

	set {arg led=0, state=0, color=0;
		leds[led].set(state, color, active);
	}

	setAll {arg state=0, color=0;
		leds.do {arg i; i.set(state, color, active)};
	}

	active_ {arg act=true;
		if (active != act) {
			active = act;
			if (active) {
				leds.do(_.active_(true))
			}{
				leds.do(_.active_(false))
			}
		}
	}

	// use active to disable without changing state
	pr_allOff {
		leds.do(_.off);
	}

}

SoftStepLedGroupSwitcher {

	var <index;
	var <>sendNames;
	var <groupsDict;
	var <names;
	var <currName;

	*new { |groups, index=0, sendNames=true|
		^super.newCopyArgs(index, sendNames).init(groups);
	}

	init { |groups|
		groupsDict =();
		names = Array(15);
		groups.do { |i|
			groupsDict.put(i.name.asSymbol, i);
			names = names.add(i.name.asSymbol);
		};
		currName = names[index];
		groupsDict[currName].active_(true);
		this.display;
	}

	up {
		this.byIndex(index + 1);
	}

	down {
		this.byIndex(index - 1)
	}

	byIndex {arg ind;
		groupsDict[currName].active_(false);
		index = ind.wrap(0, groupsDict.size - 1);
		currName = names[index];
		groupsDict[currName].active_(true);
		this.display;
	}

	byName { |name|
		if (name != currName and:{groupsDict.at(name).notNil}) {
			this.byIndex(names.indexOf(name));
		}
	}

	display {
		if (sendNames) {SoftStep.display.shortText(currName)};
	}

}
