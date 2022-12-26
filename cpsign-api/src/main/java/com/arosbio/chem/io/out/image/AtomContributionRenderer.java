package com.arosbio.chem.io.out.image;

import java.util.HashMap;
import java.util.Map;

import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.cheminf.SignificantSignature;
import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;

public class AtomContributionRenderer extends RendererTemplate<AtomContributionRenderer> {

    public static class Builder extends RendererTemplate.Builder<AtomContributionRenderer,Builder> {

        protected Builder getThis(){
            return this;
        }

        public Builder(){
            super(GradientFactory.getDefaultBloomGradient());
        }

        public Builder colorScheme(ColorGradient gradient){
            if (gradient != null)
                super.figBuilder.color(gradient);
            else
                this.figBuilder.color(GradientFactory.getDefaultBloomGradient());
            return this;
        }

        public AtomContributionRenderer build(){
            return new AtomContributionRenderer(this);
        }

    }

    private AtomContributionRenderer(Builder b){
        super(b);
    }

    Map<IAtom,Double> generateColorMapping(IAtomContainer mol, SignificantSignature prediction){
        Map<IAtom, Double> grad = new HashMap<>();
		for (Map.Entry<?, Double> atom : prediction.getAtomContributions().entrySet()){
			if (atom.getKey() instanceof IAtom)
				grad.put((IAtom) atom.getKey(), atom.getValue());
			else if (atom.getKey() instanceof Integer || atom.getKey() instanceof Long)
				grad.put(mol.getAtom((Integer) atom.getKey()), atom.getValue());
			else
				throw new IllegalArgumentException("Atoms must be specified using either index (integer) or their IAtoms");
		}
        return grad;
    }
    
}
