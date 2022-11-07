/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app;

import org.junit.Test;

import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.CLIProgressBarPrinter;
import com.arosbio.cpsign.app.utils.NullProgress;

public class TestProgress {

	@Test
	public void testProgBar() throws InterruptedException {
		CLIProgressBar pb = new CLIProgressBarPrinter("some program", 10, false);
		
		Thread.sleep(1009);
		pb.stepProgress();
		pb.setCurrentTask("sdomaetaekldaslgnasdögkln sdföl adsg sad dsa ad dsa ads f");
		Thread.sleep(1009);
		pb.stepProgress();
		pb.setCurrentTask("woop woop");
		Thread.sleep(1009);
		pb.stepProgress();
		Thread.sleep(1009);
		pb.stepProgress();
		Thread.sleep(1009);
		pb.stepProgress();
		Thread.sleep(1009);
		pb.stepProgress();
		Thread.sleep(1509);
		pb.finish();
		Thread.sleep(2000);
		pb.stepProgress();
		pb.stepProgress();
		pb.addAdditionalStep();
	}
	
	@Test
	public void testNullProgress() throws InterruptedException {
		CLIProgressBar pb = new NullProgress();
		
		Thread.sleep(100);
		pb.stepProgress();
		pb.setCurrentTask("sdomaetaekldaslgnasdögkln sdföl adsg sad dsa ad dsa ads f");
		Thread.sleep(100);
		pb.stepProgress();
		pb.setCurrentTask("woop woop");
		Thread.sleep(100);
		pb.stepProgress();
		Thread.sleep(100);
		pb.stepProgress();
		Thread.sleep(100);
		pb.stepProgress();
		Thread.sleep(100);
		pb.stepProgress();
		Thread.sleep(150);
		pb.finish();
		pb.stepProgress();
		Thread.sleep(200);
	}
	
}
