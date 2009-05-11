// this plugin is a merge of the Time_Stamper plugins from ImageJ and from Tony Collins' plugin collection at macbiophotonics. 
// it aims to combine all the functionality of both plugins and refine and enhance their functionality.

// It does not know about hyper stacks - multiple channels..... only works as expected for normal stacks.
// That meeans a single channel time series stack. 

// Dan White MPI-CBG , begin hacking on 15.04.09


import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

public class Time_Stamper_Enhanced implements PlugInFilter {
	// declare the variables we are going to use in the plugin
	ImagePlus imp;
	double time;
	static int x = 2;
	static int y = 15;
	static int size = 12;
//	int maxWidth; // maxWidth is now a method. 
	Font font;
	static double start = 0;
	static double interval = 1;
	static String timeString = "";
	static String customSuffix = "";
	static String chosenSuffix = "s";
	static String suffix = chosenSuffix;
	static int decimalPlaces = 3;
	boolean canceled;
	static String digitalOrDecimal = "decimal";
	boolean AAtext = true;
	int frame, first, last;  //these default to 0 as no values are given

	// setup the plugin and tell imagej it needs to work on a stack
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(Time_Stamper_Enhanced.class);
		if (imp!=null) {
			first = 1;
			last = imp.getStackSize();
		}
		return DOES_ALL+DOES_STACKS+STACK_REQUIRED;
	}

	// run the plugin on the ip object, which is the ImageProcessor object associated with the open/selected image. 
	public void run(ImageProcessor ip) {

		// this increments frame integer by 1. If an int is declared with no value, it defaults to 0
		frame++;
		if (frame==1) showDialog(ip);		// if at the 1st frame of the stack, show the GUI by calling the showDialog method
		if (canceled || frame<first || frame>last) return;
		
		if (frame==last) imp.updateAndDraw(); 	// Updates this image from the pixel data in its associated
							// ImageProcessor object and then displays it
							// if it is the last frame. Why do we need this when there is
							// ip.drawString(timeString); below?
	
	
		// position the time stamp string correctly, so it is all on the image, even for the last frames with bigger numbers. 
		// ip.moveTo(x, y);  // move to x y position for Timestamp writing 
		
		// this next line tries to move the time stamp right a bit to account for the max length the time stamp will be.
		// it's nice to not have the time stamp run off the right edge of the image. 
		// how about subtracting the 
		// maxWidth from the width of the image (x dimension) only if its so close that it will run off.
		// this seems to work now with digital and decimal time formats. 

		if (maxWidth(ip, start, interval, last) > ( ip.getWidth() - x ) )
			ip.moveTo( (ip.getWidth() - maxWidth(ip, start, interval, last)), y);
		else ip.moveTo(x, y);
		
		ip.drawString(timeString()); // draw the timestring into the image
		time += interval;  // increments the time by the time interval

	}

	// make the GUI for the plugin, with fields to fill all the variables we need. 
	void showDialog(ImageProcessor ip) {
		
		// here is a list of SI? approved time units for a drop down list to choose from 
		String[] timeUnitsOptions =  { "y", "d", "h", "min", "s", "ms", "�s", "ns", "ps", "fs", "as", "Custom Suffix"};
		String[] timeFormats = {"Decimal", "hh:mm:ss.ms"};
		
		// This makes the actual GUI 
		GenericDialog gd = new GenericDialog("Time Stamper Enhanced");
		
		// these are the fields of the GUI
		
		// this is a choice between digital or decimal
		// but what about mm:ss 
		// options are in the string array timeFormats, default is Decimal:  something.somethingelse 
		gd.addChoice("Time format:", timeFormats, timeFormats[0]); 
		
		// we can choose time units from a drop down list, list defined in timeunitsoptions
		gd.addChoice("Time units:", timeUnitsOptions, timeUnitsOptions[4]); 
		
		// we can set a custom suffix and use that by selecting custom siffic in the time units drop down list above
		gd.addStringField("Custom Suffix:", customSuffix);
		gd.addNumericField("Starting Time (in s if digital):", start, 2);
		gd.addNumericField("Time Interval Between Frames (in s if digital):", interval, 2);
		gd.addNumericField("X Location:", x, 0);
		gd.addNumericField("Y Location:", y, 0);
		gd.addNumericField("Font Size:", size, 0);
		gd.addNumericField("Decimal Places:", decimalPlaces, 0);
		gd.addNumericField("First Frame:", first, 0);
		gd.addNumericField("Last Frame:", last, 0);

		gd.addCheckbox("Anti-Aliased text?", true);
		
		
		
		gd.showDialog();  // shows the dialog GUI!
		
		// handle the plugin cancel button being pressed.
		if (gd.wasCanceled())
			{canceled = true; return;}
		
		// This reads user input parameters from the GUI
		digitalOrDecimal = gd.getNextChoice();
		chosenSuffix = gd.getNextChoice();
		customSuffix = gd.getNextString();
		start = gd.getNextNumber();
 		interval = gd.getNextNumber();
 		x = (int)gd.getNextNumber();
		y = (int)gd.getNextNumber();
		size = (int)gd.getNextNumber();
		decimalPlaces = (int)gd.getNextNumber();
		first = (int)gd.getNextNumber();
		last = (int)gd.getNextNumber();
		AAtext = gd.getNextBoolean(); 
		
		
		// Here we work out the size of the font to use from the size of the ROI box drawn, if one was drawn (how does it know?)
		// and set x and y at the ROI if there is one (how does it know?), so time stamp is drawn there, not at default x and y. 
		Rectangle roi = ip.getRoi();
		// set the xy time stamp drawing position for ROI is smaller than the image to bottom left of ROI
		if (roi.width<ip.getWidth() || roi.height<ip.getHeight()) {
			x = roi.x;  			// left of the ROI
			y = roi.y+roi.height;  		// bottom of the ROI
			
			// whats up with these numbers? Are they special?
			// single characters fit the ROI, but if the time stamper string is long
			// then the font is too big to fit the whole thing in!
			size = (int) (roi.height); // - 1.10526)/0.934211);	
		
		// make sure the font is not too big or small.... but why? Too -  small cant read it. Too Big - ?
		// should this use private and public and get / set methods?
		// in any case it doesnt seem to work... i can set the font < 7  and it is printed that small. 
		if (size<7) size = 7;
		if (size>80) size = 80;
		//else x and y are defaulted to 0 or set according to text in gui... 
		}
			
		// make sure the y position is not less than the font height: size, 
		// so the time stamp is not off the bottom of the image?
		if (y<size)
			y = size;
    		
		
		// set the font
		font = new Font("SansSerif", Font.PLAIN, size);
		ip.setFont(font);
		
		//more font related setting stuff moved from the run method
		// seems to work more reliable with this code in this method instead of in run.
		// But if i dont change the font size by typing in the GUI - it ignores the AA text setting!!! Why??? 
		//ip.setFont(font); //dont need that twice?
		ip.setColor(Toolbar.getForegroundColor());
		ip.setAntialiasedText(AAtext);
		
		// initialise time with the value of the starting time
		time = start; 
		
		imp.startTiming(); //What is this for?
	}	
	
	
	// Here we make the strings to print into the images. 
	
		// decide if the time format is digital or decimal according to the plugin GUI input
		// if it is decimal (not digital) then need to set suffix from drop down list
		// which might be custom suffix if one is entered and selected.
		// if it is digital, then there is no suffix as format is set hh:mm:ss.ms
		
	String timeString() {
		if (digitalOrDecimal.equals("hh:mm:ss.ms"))
			return digitalString(time);
		else if (digitalOrDecimal.equals("Decimal"))
			return decimalString(time);
		else return ("digitalOrDecimal was not selected!");
		// IJ.log("Error occurred: digitalOrDecimal must be hh:mm:ss.ms or Decimal, but it was not."); 
	}
	
		// is there a non empty string in the custom suffix box in the dialog GUI?
		// if so use it as suffix
	String suffix() {
		if (chosenSuffix.equals("Custom Suffix"))
			return customSuffix;
		else
			return chosenSuffix;
	}
	
		// makes the string containing the number for the time stamp, 
		// with specified decimal places 
		// format is decimal number with specificed no of digits after the point
		// if specificed no. of decimal places is 0 then just return the
		// specified suffix
	String decimalString(double time) { 
		if (interval==0.0) 
			return suffix(); 
		else
			return (decimalPlaces == 0 ? ""+(int)time : IJ.d2s(time, decimalPlaces)) + " " + suffix(); 
	}	
	
	// this method  adds a preceeding 0 to a number if it only has one digit instead of two. 
	// Which is handy for making 00:00 type format strings later. Thx Dscho.
	String twoDigits(int value) {
		return (value < 10 ? "0" : "") + value;
	}
	
	// makes the string containing the number for the time stamp,
	// with hh:mm:ss.decimalPlaces format
	// which is nice, but also really need hh:mm:ss and mm:ss.ms etc. 
	// could use the java time/date formating stuff for that?
	String digitalString(double time) {
		int hour = (int)(time / 3600);
		time -= hour * 3600;
		int minute = (int)(time / 60);
		time -= minute * 60;
		return twoDigits(hour) + ":" + twoDigits(minute) + ":"
			+ (time < 10 ? "0" : "") 
			+ IJ.d2s(time, decimalPlaces);
	}

//moved out of run method to here.
		// maxWidth is an integer = length of the decimal time stamp string in pixels
		// for the last slice of the stack to be stamped. It is used in the run method below, 
		// to prevent the time stamp running off the right edge of the image
		// ip.getStringWidth(string) seems to return the # of pixels long a string is in x?
		// how does it take care of font size i wonder? The font is set 
		// using the variable size... so i guess the ip object knows how big the font is.  
		// used to be: maxWidth = ip.getStringWidth(decimalString(start + interval*imp.getStackSize())); 
		// but should use last not stacksize, since no time stamp is made for slices after last?
		// It also needs to calcualte maxWidth for both digital and decimal time formats:
	int maxWidth(ImageProcessor ip, double startTime, double intervalTime, int lastFrame) {
		if (digitalOrDecimal.equals ("Decimal"))
			return ip.getStringWidth(decimalString(startTime + intervalTime*lastFrame));
		else if (digitalOrDecimal.equals ("hh:mm:ss.ms"))
			return ip.getStringWidth(digitalString(startTime + intervalTime*lastFrame));
		else return 1;  // IJ.log("Error occured: digitalOrDecimal was not selected!"); //+ message());
		// IJ.log("Error occurred: digitalOrDecimal must be hh:mm:ss.ms or Decimal, but it was not."); 
	}
	
	
}	// thats the end of Time_Stamper_Enhanced class


