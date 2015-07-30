package com.rsicms.rsuite.demodeepcopy.advisors;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.MetaDataItem;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.control.DefaultManagedObjectAdvisor;
import com.reallysi.rsuite.api.control.ManagedObjectAdvisorAttachContext;
import com.reallysi.rsuite.api.control.ManagedObjectAdvisorBeforeInsertContext;
import com.reallysi.rsuite.api.control.ManagedObjectAdvisorBeforeUpdateContext;
import com.reallysi.rsuite.api.control.ManagedObjectAdvisorContext;
import com.reallysi.rsuite.api.control.ManagedObjectAdvisorCopyContentAssemblyContext;
import com.reallysi.rsuite.api.control.MetaDataContainer;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.rsicms.pluginUtilities.LmdUtils;
import com.rsicms.rsuite.demodeepcopy.Constants;

/**
 * Adjust metadata during copy per configuration.
 * 
 */
public class LmdSettingManagedObjectAdvisor extends DefaultManagedObjectAdvisor {

    private static Log log = LogFactory.getLog(LmdSettingManagedObjectAdvisor.class);

    public void adviseDuringAttach(ExecutionContext context, ManagedObjectAdvisorAttachContext attachContext) throws RSuiteException {
        String moid = attachContext.getManagedObject().getId();
        log.info("adviseDuringAttach(): getId()=" + moid);
    }

    public void adviseDuringCopyContentAssembly(ExecutionContext context, ManagedObjectAdvisorCopyContentAssemblyContext copyContext) throws RSuiteException {
        log.info("adviseDuringCopyContentAssembly");
    }

    public void adviseBeforeUpdate(ExecutionContext context, ManagedObjectAdvisorBeforeUpdateContext updateContext) throws RSuiteException {
        String moid = updateContext.getId();
        log.info("adviseBeforeUpdate(): getId()=" + moid);
    }
    
    public void adviseDuringUpdate(ExecutionContext context, ManagedObjectAdvisorContext updateContext) throws RSuiteException {
        String moid = updateContext.getId();
        log.info("adviseDuringUpdate(): getId()=" + moid);
    }
    
    public void adviseBeforeInsert(ExecutionContext context, ManagedObjectAdvisorBeforeInsertContext insertContext) throws RSuiteException {
        log.info("adviseBeforeInsert()");
    }

    public void adviseDuringInsert(ExecutionContext context, ManagedObjectAdvisorContext insertContext) throws RSuiteException {
        String moid = insertContext.getId();
        log.info("adviseDuringInsert(): insertContext.getId()=" + moid);

        Map<String, Object> callContext = insertContext.getCallContext();
        String copySource = callContext.get("copyContext").toString();

        MetaDataContainer mdc = insertContext.getMetaDataContainer();
        setSource(context, insertContext, mdc, copySource);
    }

    public void setSource(ExecutionContext context, ManagedObjectAdvisorContext advisorContext, MetaDataContainer mdc, String copySource)
            throws RSuiteException {
        List<MetaDataItem> mdis = mdc.getMetaDataItemList();

        if (copySource != null) {
            LmdUtils.createLmdFieldIfDoesntExist(context, Constants.SOURCE_LMD, false, true, true);
            LmdUtils.addElementToLmdIfNotAlready(context, Constants.SOURCE_LMD, advisorContext.getElement().getNamespaceURI(), advisorContext.getElement()
                    .getLocalName());
            Boolean updatedSource = false;
            for (MetaDataItem mdi : mdis) {
                if (mdi.getName().equals(Constants.SOURCE_LMD)) {
                    mdc.updateMetaDataItem(mdi.getId(), Constants.SOURCE_LMD, copySource);
                    updatedSource = true;
                }
            }
            if (!updatedSource) {
                mdc.addMetaDataItem(Constants.SOURCE_LMD, copySource);
            }
        }
    }

}
