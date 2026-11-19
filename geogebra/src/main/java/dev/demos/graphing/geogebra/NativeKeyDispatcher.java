package dev.demos.graphing.geogebra;

import java.util.List;
import org.geogebra.common.kernel.geos.GeoElement;
import org.geogebra.common.main.App;
import org.geogebra.common.main.GlobalKeyDispatcher;
import org.geogebra.editor.share.util.KeyCodes;

/** DOM-free adapter to the original web/shared keyboard dispatch order. */
final class NativeKeyDispatcher extends GlobalKeyDispatcher {
    private boolean space;
    NativeKeyDispatcher(App app){super(app);}
    void dispatch(String name,boolean down,int modifiers){
        KeyCodes key;
        try{key=KeyCodes.valueOf(name);}catch(IllegalArgumentException unknown){return;}
        boolean shift=(modifiers&2)!=0,control=(modifiers&12)!=0,alt=(modifiers&1)!=0;
        if(key==KeyCodes.SPACE)space=down;
        updateKeyDownFlags(space,control,shift);
        if(down){
            boolean handled=control&&!alt&&handleCtrlKeys(key,shift,false,true);
            if(!handled)handleSelectedGeosKeys(key,selection.getSelectedGeos(),shift,control,alt,false);
        }else{
            // Only viewport keys reach this host. Browser/application navigation
            // and text fields remain with the native application UI.
            if(key==KeyCodes.ESCAPE || key==KeyCodes.SPACE || key==KeyCodes.ENTER
                    || key==KeyCodes.SHIFT || key==KeyCodes.F9)
                handleGeneralKeys(key,shift,control,alt,false,true);
            storeUndoInfoIfChanged();
        }
    }
    protected void createNewWindow(){}
    protected void showPrintPreview(App app){}
    protected boolean handleCtrlShiftN(boolean alt){return false;}
    protected void copyDefinitionsToInputBarAsList(List<GeoElement> geos){}
}
