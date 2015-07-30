package com.rsicms.rsuite.demodeepcopy.webservice;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.Alias;
import com.reallysi.rsuite.api.ContentAssemblyItem;
import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.MetaDataItem;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.content.ContentDisplayObject;
import com.reallysi.rsuite.api.content.ContentObjectPath;
import com.reallysi.rsuite.api.control.ContentAssemblyCreateOptions;
import com.reallysi.rsuite.api.control.ManagedObjectAdvisor;
import com.reallysi.rsuite.api.control.ManagedObjectAdvisorContext;
import com.reallysi.rsuite.api.control.MetaDataContainer;
import com.reallysi.rsuite.api.control.ObjectAttachOptions;
import com.reallysi.rsuite.api.control.ObjectCopyOptions;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.reallysi.rsuite.api.remoteapi.CallArgument;
import com.reallysi.rsuite.api.remoteapi.CallArgumentList;
import com.reallysi.rsuite.api.remoteapi.DefaultRemoteApiHandler;
import com.reallysi.rsuite.api.remoteapi.RemoteApiExecutionContext;
import com.reallysi.rsuite.api.remoteapi.RemoteApiResult;
import com.reallysi.rsuite.api.remoteapi.result.MessageDialogResult;
import com.reallysi.rsuite.api.remoteapi.result.MessageType;
import com.reallysi.rsuite.api.remoteapi.result.NotificationResult;
import com.reallysi.rsuite.api.remoteapi.result.RestResult;
import com.reallysi.rsuite.service.ContentAssemblyService;
import com.reallysi.rsuite.service.ManagedObjectService;
import com.rsicms.pluginUtilities.LmdUtils;
import com.rsicms.rsuite.demodeepcopy.Constants;
import com.rsicms.rsuite.demodeepcopy.advisors.LmdSettingManagedObjectAdvisor;
import com.rsicms.rsuite.helpers.utils.RSuiteUtils;

public class DeepCopy extends DefaultRemoteApiHandler {

    private static Log log = LogFactory.getLog(DeepCopy.class);
    private Boolean dupContent = true;
    // private Boolean dupAssignments = true;
    private String copySuffix = "";
    private String sourceLabel = "";

    @Override
    public RemoteApiResult execute(RemoteApiExecutionContext context, CallArgumentList args) throws RSuiteException {

        log.info("Returned arguments are: ");
        for (CallArgument arg : args.getAll()) {
            log.info("  " + arg.getName() + " = " + arg.getValue());
        }

        User user = context.getSession().getUser();
        String caId = args.getFirstString("rsuiteId");
        String copyLabel = args.getFirstString("copyLabel");
        String copyContent = args.getFirstString("copyContent");
        if (copyContent.equals("reuse"))
            dupContent = false;
        sourceLabel = args.getFirstString("sourceLabel");
        copySuffix = args.getFirstString("copySuffix");
        // String copyAssignments = args.getFirstString("copyAssignments");
        // if (copyAssignments.equals("reuse"))
        // dupAssignments = false;

        if (dupContent && (copySuffix == null || copySuffix.isEmpty())) {
            Calendar calendar = Calendar.getInstance();
            copySuffix = Long.toString(calendar.getTimeInMillis());
        }

        String returnMessageText = "";
        String moIdList = "";
        ContentAssemblyService casvc = context.getContentAssemblyService();
        ManagedObjectService mosvc = context.getManagedObjectService();

        try {
            ContentObjectPath path = args.getFirstContentObjectPath(user);
            String parentCaId = casvc.getRootFolder(user).getId();
            if (path != null) {
                if (path.getPathObjects().size() > 1) {
                    ContentDisplayObject parentObject = path.getPathObjects().get(path.getPathObjects().size() - 2);
                    parentCaId = parentObject.getId();
                    ManagedObject parentCaMo = mosvc.getManagedObject(user, parentCaId);
                    parentCaId = RSuiteUtils.getRealMo(context, user, parentCaMo).getId();
                }
                moIdList = parentCaId;

                ContentAssemblyItem ca = casvc.getContentAssemblyNodeContainer(user, caId);
                String caType = ca.getType();
                List<MetaDataItem> metaDataItems = ca.getMetaDataItems();

                ContentAssemblyCreateOptions options = new ContentAssemblyCreateOptions();
                options.setType(caType);
                options.setMetaDataItems(metaDataItems);
                ContentAssemblyItem newCa = casvc.createContentAssembly(user, parentCaId, copyLabel, options);
                ManagedObjectAdvisor advisor = new LmdSettingManagedObjectAdvisor();
                copyKids(context, advisor, user, ca, newCa);
                returnMessageText = "A copy of " + ca.getDisplayName() + " has been created.";
            } else {
                log.error("ERROR: args.getFirstContentObjectPath(user) was null and never should be. Copy could not be performed.");
                return new MessageDialogResult(MessageType.FAILURE, "Create Copy Failure",
                        "System error. Please notify your administrator. <br/>Content object path was null.");
            }
        } catch (Throwable e) {
            log.error(e.getMessage());
            return new MessageDialogResult(MessageType.FAILURE, "Copy Folder Failure", "System error. Please notify your administrator. <br/>"
                    + e.getMessage());
        }

        RestResult result = new NotificationResult(returnMessageText);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("objects", moIdList);
        params.put("children", true);
        result.addAction("rsuite:refreshManagedObjects", params);

        return result;
    }

    private void copyKids(ExecutionContext context, ManagedObjectAdvisor advisor, User user, ContentAssemblyItem ca, ContentAssemblyItem newCa)
            throws RSuiteException {
        List<? extends ContentAssemblyItem> kids = ca.getChildrenObjects();
        for (ContentAssemblyItem kid : kids) {
            ContentAssemblyService casvc = context.getContentAssemblyService();
            ManagedObjectService mosvc = context.getManagedObjectService();
            String type = kid.getObjectType().name();
            ContentAssemblyItem newKid = null;
            if (type.equals("CONTENT_ASSEMBLY_REF")) {
                // get the actual child node instead of the ref
                String kidMoid = mosvc.getManagedObject(user, kid.getId()).getTargetId();
                kid = casvc.getContentAssemblyNodeContainer(user, kidMoid);
                String caType = kid.getType();
                List<MetaDataItem> metaDataItems = kid.getMetaDataItems();
                ContentAssemblyCreateOptions options = new ContentAssemblyCreateOptions();
                options.setType(caType);
                options.setMetaDataItems(metaDataItems);
                newKid = casvc.createContentAssembly(user, newCa.getId(), kid.getDisplayName(), options);
            }
            if (type.equals("MANAGED_OBJECT_REF")) {
                // add the mo to the new copy
                ManagedObject moToAttach = mosvc.getManagedObject(user, mosvc.getManagedObject(user, kid.getId()).getTargetId());
                if (dupContent) {
                    ObjectCopyOptions copyOptions = new ObjectCopyOptions();
                    setAliasAndFilenameOptions(moToAttach, copyOptions);
                    // TODO handle variants (need advisor)
                    moToAttach = mosvc.copy(user, moToAttach.getId(), copyOptions);
                }
                ObjectAttachOptions attachOptions = new ObjectAttachOptions();
                // TODO handle contextual metadata
                ManagedObject newMoRef = casvc.attach(user, newCa.getId(), moToAttach.getId(), attachOptions);
                ManagedObject newMo = RSuiteUtils.getRealMo(context, user, newMoRef);
                setSource(context, user, newMo);
            } else {
                // CONTENT_ASSEMBLY_NODE or CONTENT_ASSEMBLY
                ContentAssemblyItem kidNode = casvc.getContentAssemblyNodeContainer(user, kid.getId());
                copyKids(context, advisor, user, kidNode, newKid);
            }
        }
    }

    public void setAliasAndFilenameOptions(ManagedObject moToAttach, ObjectCopyOptions copyOptions) throws RSuiteException {
        String newFn = moToAttach.getId() + copySuffix;
        String ext = "";
        // TODO better handling if no filename alias
        Alias[] fn = moToAttach.getAliases("filename");
        if (fn != null && fn.length > 0) {
            newFn = FilenameUtils.getBaseName(fn[0].getText()) + copySuffix;
            ext = "." + FilenameUtils.getExtension(fn[0].getText());
        }
        Alias[] aliases = moToAttach.getAliases();
        int acount = 0;
        for (Alias a : aliases) {
            if (a.getType() == null) {
            } else if (a.getType().equals("filename")) {
                aliases[acount] = new Alias(newFn + ext, "filename");
            } else if (a.getType().equals("basename")) {
                aliases[acount] = new Alias(newFn, "basename");
            }
            acount++;
        }
        copyOptions.setAliases(aliases);
        copyOptions.setExternalFileName(newFn + ext);
        if (moToAttach.isNonXml()) {
            copyOptions.setDisplayName(newFn + ext);
        }
    }

    public void setSource(ExecutionContext context, User user, ManagedObject mo)
            throws RSuiteException {

        if (sourceLabel != null) {
            LmdUtils.createLmdFieldIfDoesntExist(context, Constants.SOURCE_LMD, false, true, true);
            LmdUtils.addElementToLmdIfNotAlready(context, Constants.SOURCE_LMD, mo.getNamespaceURI(), mo.getLocalName());
            context.getManagedObjectService().setMetaDataEntry(user, mo.getId(), new MetaDataItem(Constants.SOURCE_LMD, sourceLabel));

//            List<MetaDataItem> mdis = mo.getMetaDataItems();
//            Boolean updatedSource = false;
//            for (MetaDataItem mdi : mdis) {
//                if (mdi.getName().equals(Constants.SOURCE_LMD)) {
//                    context.getManagedObjectService().setMetaDataEntry(user, mo.getId(), new MetaDataItem(Constants.SOURCE_LMD, copySource));
//                    updatedSource = true;
//                }
//            }
//            if (!updatedSource) {
//                mdc.addMetaDataItem(Constants.SOURCE_LMD, copySource);
//            }
        }
    }
}