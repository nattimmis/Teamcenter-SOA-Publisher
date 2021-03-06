package com.googlecode.bushel.ivy;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.License;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser;
import org.apache.ivy.plugins.repository.url.URLResource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class BushelIvyParser extends XmlModuleDescriptorParser {

    public static class BushelParser extends Parser {

        private ModuleDescriptor manifestMD = null;

        public BushelParser(ModuleDescriptorParser parser, ParserSettings ivySettings) {
            super(parser, ivySettings);
        }

        @Override
        protected void infoStarted(Attributes attributes) {
            String manifest = attributes.getValue("manifest");
            if (manifest != null) {
                try {
                    manifestMD = parseManifest(manifest);
                    includeMdInfo(getMd(), manifestMD);
                } catch (SAXException e) {
                    // it is caught in the startElement method
                    throw new RuntimeException(e);
                }
                return;
            }
            super.infoStarted(attributes);
        }

        public ModuleDescriptor parseManifest(String manifest) throws SAXException {
            if (getDescriptorURL() == null) {
                throw new SAXException(
                        "A reference to a manifest is only supported on module descriptors which are parsed from an URL");
            }
            URL includedUrl;
            try {
                includedUrl = getSettings().getRelativeUrlResolver().getURL(getDescriptorURL(), manifest);
            } catch (MalformedURLException e) {
                SAXException pe = new SAXException("Incorrect relative url of the include in '" + getDescriptorURL()
                        + "' (" + e.getMessage() + ")");
                pe.initCause(e);
                throw pe;
            }
            URLResource includeResource = new URLResource(includedUrl);
            ModuleDescriptorParser parser = ModuleDescriptorParserRegistry.getInstance().getParser(includeResource);
            ModuleDescriptor manifestMd;
            try {
                manifestMd = parser.parseDescriptor(getSettings(), includeResource.getURL(), includeResource,
                        isValidate());
            } catch (ParseException e) {
                SAXException pe = new SAXException("Incorrect included md '" + includeResource + "' in '"
                        + getDescriptorURL() + "' (" + e.getMessage() + ")");
                pe.initCause(e);
                throw pe;
            } catch (IOException e) {
                SAXException pe = new SAXException("Unreadable included md '" + includeResource + "' in '"
                        + getDescriptorURL() + "' (" + e.getMessage() + ")");
                pe.initCause(e);
                throw pe;
            }
            return manifestMd;
        }

        @Override
        public void endDocument() throws SAXException {
            if (manifestMD != null) {
                includeMdDepedencies(getMd(), manifestMD);
            }
        }
    }

    @Override
    protected Parser newParser(ParserSettings ivySettings) {
        return new BushelParser(this, ivySettings);
    }

    private static void includeMdInfo(DefaultModuleDescriptor md, ModuleDescriptor include) {
        ModuleRevisionId mrid = include.getModuleRevisionId();
        if (mrid != null) {
            md.setModuleRevisionId(mrid);
        }

        ModuleRevisionId resolvedMrid = include.getResolvedModuleRevisionId();
        if (resolvedMrid != null) {
            md.setResolvedModuleRevisionId(resolvedMrid);
        }

        String description = include.getDescription();
        if (description != null) {
            md.setDescription(description);
        }

        String homePage = include.getHomePage();
        if (homePage != null) {
            md.setHomePage(homePage);
        }

        long lastModified = include.getLastModified();
        if (lastModified > md.getLastModified()) {
            md.setLastModified(lastModified);
        }

        String status = include.getStatus();
        if (status != null) {
            md.setStatus(status);
        }

        Map<String, String> extraInfo = include.getExtraInfo();
        if (extraInfo != null) {
            for (Entry<String, String> info : extraInfo.entrySet()) {
                md.addExtraInfo(info.getKey(), info.getValue());
            }
        }

        License[] licenses = include.getLicenses();
        if (licenses != null) {
            for (int i = 0; i < licenses.length; i++) {
                md.addLicense(licenses[i]);
            }
        }

        Configuration[] configurations = include.getConfigurations();
        if (configurations != null) {
            for (int i = 0; i < configurations.length; i++) {
                md.addConfiguration(configurations[i]);
            }
        }

    }

    private static void includeMdDepedencies(DefaultModuleDescriptor md, ModuleDescriptor include) {
        Artifact[] artifacts = include.getAllArtifacts();
        if (artifacts != null) {
            for (int i = 0; i < artifacts.length; i++) {
                String[] artifactConfs = artifacts[i].getConfigurations();
                for (int j = 0; j < artifactConfs.length; j++) {
                    md.addArtifact(artifactConfs[j], artifacts[i]);
                }
            }
        }

        DependencyDescriptor[] dependencies = include.getDependencies();
        if (dependencies != null) {
            for (int i = 0; i < dependencies.length; i++) {
                md.addDependency(dependencies[i]);
            }
        }

        ExcludeRule[] excludeRules = include.getAllExcludeRules();
        if (excludeRules != null) {
            for (int i = 0; i < excludeRules.length; i++) {
                md.addExcludeRule(excludeRules[i]);
            }
        }
    }
}
