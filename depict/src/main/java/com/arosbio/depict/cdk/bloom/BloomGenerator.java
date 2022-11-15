/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.depict.cdk.bloom;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.depict.cdk.bloom.utils.Utils;

import org.openscience.cdk.config.Elements;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.renderer.RendererModel;
import org.openscience.cdk.renderer.generators.IGenerator;
import org.openscience.cdk.renderer.generators.IGeneratorParameter;

public class BloomGenerator implements IGenerator<IAtomContainer> {

       public static final String DS_DATA = "net.bioclipe.ds.data";

       public List<IGeneratorParameter<?>> getParameters() {
              return Collections.emptyList();
       }

       @Override
       public BloomElement generate(IAtomContainer object, RendererModel model) {
              BloomElement element = null;

              Map<?,?> colorMap = object.getProperty(DS_DATA,Map.class);

              for (IAtom atom : object.atoms()){
                     // Create the element once we know the coordinates of the first atom
                     if (element == null){
                            element = new BloomElement(atom.getPoint2d().x, atom.getPoint2d().y);
                     }

                     Double value = null;
                     if (colorMap!=null){
                            try {
                                   value = (double) colorMap.get(atom);
                            } catch (Exception e){}
                            if (value == null){
                                   try {
                                          value = (double) colorMap.get(atom.getIndex());
                                   } catch (Exception e){}
                            }
                            
                     } 
                     if (value == null) {
                            // No mapping set on the molecule, looking at each atom
                            value = atom.getProperty(DS_DATA, Double.class);
                     }
                     
                     if (value==null){
                            value = 0d;
                     }
                     
                     // Gradient are between -1..1
                     value = Utils.truncate(value, ColorGradient.MIN_VALUE, ColorGradient.MAX_VALUE);

                     Double vdwRadius = Elements.ofString(atom.getSymbol()).vdwRadius(); 
                     if (vdwRadius == null) {
                            vdwRadius = .5; 
                     }
                     BloomElement.BloomingPoint gp = new BloomElement.BloomingPoint(
                            atom.getPoint2d().x,
                            atom.getPoint2d().y,
                            vdwRadius,
                            value);
                     element.add(gp);

              }
              return element;
       }

}
