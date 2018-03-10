SoftStep {

	classvar sysEx;
	classvar <initialized=false;
	classvar <in;
	classvar <out;
	classvar <mode = 0;		// 0 - standalone, 1 - tether
	classvar <backlight=0;		// backlight on or off
	classvar <isPlaying=false;
	classvar <rout;       // Routine used by the longText method
	classvar <index;      // on Linux I need the index of the MIDIEndPoint in the destinations array to connect to it

	// Looks for Midi ports and creates MIDIIn and MIDIOut.
	// Must be run before other methods can be called
	*connect { arg switchToHosted=true;
		if (initialized) {^true};
		// setup midi
		if(MIDIClient.initialized.not,{MIDIIn.connectAll});

		MIDIClient.destinations.do {arg i, j;
			if (i.device.contains("SSCOM") and: {i.name.contains("2").not}) {
				out = MIDIOut.newByName(i.device, i.name).latency_(0.0);
				index = j;
			};
		};

		// on Linux the MIDIOut port has to be connected
		if (thisProcess.platform.name == \linux) {
			out.connect(index);
		};

		MIDIClient.sources.do {arg i;
			if (i.device.contains("SSCOM") and: {i.name.contains("2").not}) {
				in = MIDIIn.findPort(i.device, i.name);
			};
		};

		if (out.isNil or: {in.isNil}, {Error("Softstep hardware not found").throw});

		initialized = true;
		// switch to hosted mode
		if (switchToHosted) { this.mode_(1) };
	}

	// switches softstep mode (1 -> hosted/tether, 0 -> standalone)
	*mode_ { |md=1|
		var msg1, msg2;
		if (initialized.not) {"SoftStep must first be initialized.".warn; ^nil};
		if (md == 1, {
			msg1 = sysEx[\standaloneOff];
			msg2 = sysEx[\tetherOn];
		}, {
			this.shortText("disc");
			msg1 = sysEx[\tetherOff];
			msg2 = sysEx[\standaloneOn];
		});
		if (1 - mode == md) { // only send sysex if mode changes
			{
				out.sysex(msg1);
				0.5.wait;
				out.sysex(msg2);
				0.5.wait;
				mode = md;
				if (mode==1) {this.shortText("conn")};
			}.fork(AppClock);
		};
	}

	// switches the backlight on or off
	*backlight_ {|val=0|
		if (initialized.not) {"SoftStep must first be initialized.".warn; ^nil};
		if (val == 0)
			{backlight = 0; out.sysex(sysEx[\lightOff])}
			{backlight = 1; out.sysex(sysEx[\lightOn])}
	}

	*disconnect {
		if (initialized && isPlaying.not) {
			this.mode_(0);
			initialized = false;
			//if (thisProcess.platform.name == \linux) {
			//	out.disconnect(index);
			//};
		};
	}

	// methods previously found in SoftStepDisplay
	*shortText { |text=" "|
		if(isPlaying.not) {
			isPlaying = true;
			if (text.isFloat and: {text > 99}) {text = text.round};
			text = text.asString.toUpper;
			text = text.padLeft(4);
			text = text.ascii;
			text.do {|i, j| if(j < 4) {out.control(1, 50 + j, i)}};
			isPlaying = false;
		}
	}

	*longText { |text=" ", speed=0.2|
		if(isPlaying) {"Display Routine is already playing.".postln; ^nil};
		isPlaying = true;

		text = text.asString.toUpper;
		text = "   " ++ text  ++ "    "; // add 3 spaces in front and 4 in back
		text = text.ascii;
		rout = {
			(text.size-3).do {arg i;
				4.do {arg k;
					out.control(3, 50 + k, text.at(i + k));
					0.002.wait;
				};
				speed.wait;
			};
			isPlaying = false;
		}.fork(AppClock);
	}

	*clearDisplay {
		"    ".ascii.do {|i, j| out.control(1, 50 + j, i)};
	}


	*initClass {
		sysEx = (
			lightOn: Int8Array[240, 0, 27, 72, 122, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
					0, 4, 0, 5, 8, 37, 1, 32, 0, 0, 123, 44, 0, 0, 0, 12, 247],
			lightOff: Int8Array[240, 0, 27, 72, 122, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
					0, 4, 0, 5, 8, 37, 0, 32, 0, 0, 76, 28, 0, 0, 0, 12, 247],
			standaloneOn: Int8Array[240, 0, 27, 72, 122, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
					0, 9, 0, 11, 43, 58, 0, 16, 3, 1, 0, 0, 0, 0, 0, 0, 0, 104, 102, 0, 0, 0, 0, 0, 247],
			standaloneOff: Int8Array[240, 0, 27, 72, 122, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
					0, 9, 0, 11, 43, 58, 0, 16, 3, 0, 0, 0, 0, 0, 0, 0, 0, 80, 7, 0, 0, 0, 0, 0, 247],
			tetherOn: Int8Array[240, 0, 27, 72, 122, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
					0, 9, 0, 11, 43, 58, 0, 16, 4, 1, 0, 0, 0, 0, 0, 0, 0, 47, 126, 0, 0, 0, 0, 2, 247],
			tetherOff: Int8Array[240, 0, 27, 72, 122, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
					0, 9, 0, 11, 43, 58, 0, 16, 4, 0, 0, 0, 0, 0, 0, 0, 0, 23, 31, 0, 0, 0, 0, 0, 247]
		);
	}
}