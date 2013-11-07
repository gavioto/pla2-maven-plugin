/*
 * Copyright 2013 Grzegorz Slowikowski (gslowikowski at gmail dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.code.play2.provider.play21;

import java.io.File;
//?import java.io.IOException;

import play.templates.ScalaTemplateCompiler;
import play.templates.TemplateCompilationError;

import com.google.code.play2.provider.Play2TemplateCompiler;
import com.google.code.play2.provider.TemplateCompilationException;

public class Play21TemplateCompiler
    implements Play2TemplateCompiler
{
    private static final String[] resultTypes = {
        "play.api.templates.Html",
        "play.api.templates.Txt",
        "play.api.templates.Xml" };

    private static final String[] formatterTypes = {
        "play.api.templates.HtmlFormat",
        "play.api.templates.TxtFormat",
        "play.api.templates.XmlFormat" };

    private static final String[] javaAdditionalImports = new String[] {
        "play.api.templates._",
        "play.api.templates.PlayMagic._",
        "models._",
        "controllers._",
        "java.lang._",
        "java.util._",
        "scala.collection.JavaConversions._",
        "scala.collection.JavaConverters._",
        "play.api.i18n._",
        "play.core.j.PlayMagicForJava._",
        "play.mvc._",
        "play.data._",
        "play.api.data.Field",
        "play.mvc.Http.Context.Implicit._",
        "views.%format%._" };

    private static final String[] scalaAdditionalImports = new String[] {
        "play.api.templates._",
        "play.api.templates.PlayMagic._",
        "models._",
        "controllers._",
        "play.api.i18n._",
        "play.api.mvc._",
        "play.api.data._",
        "views.%format%._" };

    private String mainLang;

    private File appDirectory;

    private File outputDirectory;

    public void setMainLang( String mainLang )
    {
        this.mainLang = mainLang;
    }

    public void setAppDirectory( File appDirectory )
    {
        this.appDirectory = appDirectory;
    }

    public void setOutputDirectory( File outputDirectory )
    {
        this.outputDirectory = outputDirectory;
    }

    public void compile( File templateFile )
        throws TemplateCompilationException
    {
        String fileName = templateFile.getName();
        String ext = fileName.substring( fileName.lastIndexOf( "." ) + 1 );
        String importsAsString = getImportsAsString( ext );
        int index = -1;
        if ( "html".equals( ext ) )
        {
            index = 0;
        }
        else if ( "txt".equals( ext ) )
        {
            index = 1;
        }
        else if ( "xml".equals( ext ) )
        {
            index = 2;
        }
        if ( index >= 0 )
        {
            String resultType = resultTypes[index];
            String formatterType = formatterTypes[index];
            try
            {
                ScalaTemplateCompiler.compile( templateFile, appDirectory, outputDirectory, resultType, formatterType,
                                               importsAsString );
            }
            catch ( TemplateCompilationError e )
            {
                throw new TemplateCompilationException( e.source(), e.message(), e.line(), e.column() );
            }
        }
    }

    private String getImportsAsString( String format )
    {
        String[] additionalImports = {};
        if ( "java".equalsIgnoreCase( mainLang ) )
        {
            additionalImports = javaAdditionalImports;
        }
        else if ( "scala".equalsIgnoreCase( mainLang ) )
        {
            additionalImports = scalaAdditionalImports;
        }
        StringBuilder sb = new StringBuilder();
        for ( String additionalImport : additionalImports )
        {
            sb.append( "import " ).append( additionalImport.replace( "%format%", format ) ).append( "\n" );
        }
        return sb.substring( 0, sb.length() - 1 );
    }

}