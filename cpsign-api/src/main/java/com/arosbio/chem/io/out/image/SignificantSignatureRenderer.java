package com.arosbio.chem.io.out.image;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.cheminf.SignificantSignature;
import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.color.gradient.impl.GradientPoint;
import com.arosbio.color.gradient.impl.MultipointGradient;

public class SignificantSignatureRenderer extends RendererTemplate<SignificantSignatureRenderer> {

    private static final Color DEFAULT_HIGHLIGHT_COLOR = Color.BLUE;
    private static final Color NODE_COLOR = new Color(228,228,228); // Grayish color

    public static class Builder extends RendererTemplate.Builder<SignificantSignatureRenderer,Builder> {

        private Color highlight = DEFAULT_HIGHLIGHT_COLOR;
        private Color bloomColor = NODE_COLOR; // Same as the "node color"

        protected Builder getThis(){
            return this;
        }

        public Builder(){
            super(GradientFactory.getDefaultBloomGradient());
        }

        public Builder highlight(Color c){
            this.highlight = c != null ?  c : DEFAULT_HIGHLIGHT_COLOR;
            return this;
        }
        public Builder bloomBackground(Color c){
            this.bloomColor = c != null ? c : NODE_COLOR;
            return this;
        }

        public SignificantSignatureRenderer build(){
            this.figBuilder.color(generateGradient(bloomColor, highlight));
            return new SignificantSignatureRenderer(this);
        }

        private static ColorGradient generateGradient(Color background, Color highlight){
            return new MultipointGradient(
                    Arrays.asList(GradientPoint.of(background, 0.1f),
                    GradientPoint.of(NODE_COLOR, 0.2f),
                    GradientPoint.of(highlight, 0.4f)));
        }

    }

    private SignificantSignatureRenderer(Builder b){
        super(b);
    }

    Map<Integer,Double> generateColorMapping(IAtomContainer mol, SignificantSignature prediction){
        Map<Integer, Double> gradient = new HashMap<>();
		for(Integer atom : prediction.getAtoms())
			gradient.put(atom, 1.0);
        return gradient;
    }

    
}
