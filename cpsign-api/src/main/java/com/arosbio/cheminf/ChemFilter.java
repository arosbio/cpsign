/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf;

import java.io.Serializable;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.Named;

/**
 * A {@code ChemFilter} is used when loading molecules into a {@link ChemDataset} in order to filter out 
 * molecules that should not be part of modeling, this may be due to outliers. Note that the {@code ChemFilter}
 * needs to be set on the {@link ChemDataset} before any of the add methods are called, as they require chemical structure
 * which is lost after descriptors have been calculated. The {@link #applyToPredictions()} method is used for checking if
 * a filter needs to be stored together with the predictor model, so it can be applied to future predictions as well.
 */
public interface ChemFilter extends Described, Named, Configurable, Cloneable, Serializable {

    /**
     * Whether the filter also should be applied at prediction time, and thus be requried to be saved
     * @return {@code true} if it should be applied at prediction time, {@code false} otherwise
     */
    public boolean applyToPredictions();

    /**
     * Check if a given {@code molecule} should be keept or removed, this method can be applied both at training/data loading 
     * stage and at prediction time, depending on what {@link #applyToTrainingData()} and {@link #applyToPredictions()} returns. 
     * @param molecule the molecule to check
     * @return {@code true} if {@code molecule} should be keept (either)
     * @throws IllegalStateException If the filter needs fitting before this method, and the {@link #fit(Iterable)} has not been called
     * @throws IllegalArgumentException Any invalid arguments
     * @throws CDKException Issues handling the molecule
     */
    public boolean keep(IAtomContainer molecule) throws IllegalStateException, IllegalArgumentException, CDKException;

    /**
     * Generate a textual description of why a molecule should be discarded, i.e. {@link #keep(IAtomContainer)} returns {@code false}
     * for the given molecule.
     * @param molecule a molecule for which the {@link #keep(IAtomContainer)} returned {@code false} for
     * @return a textual description of why the molecule is discarded 
     * @throws IllegalStateException If the filter needs fitting before this method, and the {@link #fit(Iterable)} has not been called
     * @throws IllegalArgumentException Any invalid arguments, or e.g. the {@code molecule} should be keept 
     * @throws CDKException Issues handling the molecule
     */
    public String getDiscardReason(IAtomContainer molecule) throws IllegalStateException, IllegalArgumentException, CDKException;
    
}
