package org.codehaus.modello.plugin.java;

/*
 * Copyright (c) 2004, Codehaus.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import org.codehaus.modello.ModelloException;
import org.codehaus.modello.plugin.java.javasource.JClass;
import org.codehaus.modello.plugin.java.javasource.JField;
import org.codehaus.modello.plugin.java.javasource.JInterface;
import org.codehaus.modello.plugin.java.javasource.JMethod;
import org.codehaus.modello.plugin.java.javasource.JParameter;
import org.codehaus.modello.plugin.java.javasource.JSourceCode;
import org.codehaus.modello.plugin.java.javasource.JSourceWriter;
import org.codehaus.modello.plugin.java.javasource.JType;
import org.codehaus.modello.model.CodeSegment;
import org.codehaus.modello.model.Model;
import org.codehaus.modello.model.ModelAssociation;
import org.codehaus.modello.model.ModelClass;
import org.codehaus.modello.model.ModelDefault;
import org.codehaus.modello.model.ModelField;
import org.codehaus.modello.model.ModelInterface;
import org.codehaus.modello.plugin.AbstractModelloGenerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * @author <a href="mailto:jason@modello.org">Jason van Zyl </a>
 * @version $Id$
 */
public class JavaModelloGenerator
    extends AbstractModelloGenerator
{
    public void generate( Model model, Properties parameters )
        throws ModelloException
    {
        initialize( model, parameters );

        try
        {
            generateJava();
        }
        catch ( IOException ex )
        {
            throw new ModelloException( "Exception while generating Java.", ex );
        }
    }

    private void generateJava()
        throws ModelloException, IOException
    {
        Model objectModel = getModel();

        // ----------------------------------------------------------------------
        // Generate the interfaces.
        // ----------------------------------------------------------------------

        for ( Iterator i = objectModel.getInterfaces( getGeneratedVersion() ).iterator(); i.hasNext(); )
        {
            ModelInterface modelInterface = (ModelInterface) i.next();

            String packageName;

            if ( isPackageWithVersion() )
            {
                packageName = modelInterface.getPackageName( true, getGeneratedVersion() );
            }
            else
            {
                packageName = modelInterface.getPackageName( false, null );
            }

            char fileSeparator = System.getProperty( "file.separator" ).charAt( 0 );

            String directory = packageName.replace( '.', fileSeparator );

            File f = new File( new File( getOutputDirectory(), directory ), modelInterface.getName() + ".java" );

            if ( !f.getParentFile().exists() )
            {
                f.getParentFile().mkdirs();
            }

            FileWriter writer = new FileWriter( f );

            JSourceWriter sourceWriter = new JSourceWriter( writer );

            JInterface jInterface = new JInterface( modelInterface.getName() );

            jInterface.setPackageName( packageName );

            if ( modelInterface.getSuperInterface() != null )
            {
                jInterface.addInterface( modelInterface.getSuperInterface() );
            }

            if ( modelInterface.getCodeSegments( getGeneratedVersion() ) != null )
            {
                for ( Iterator iterator = modelInterface.getCodeSegments( getGeneratedVersion() ).iterator();
                      iterator.hasNext(); )
                {
                    //TODO : add this method to jInterface or remove
                    // codeSegments and add method tag

                    // CodeSegment codeSegment = (CodeSegment) iterator.next();

                    //jInterface.addSourceCode( codeSegment.getCode() );
                }
            }

            jInterface.print( sourceWriter );

            writer.flush();

            writer.close();
        }

        // ----------------------------------------------------------------------
        // Generate the classes.
        // ----------------------------------------------------------------------

        for ( Iterator i = objectModel.getClasses( getGeneratedVersion() ).iterator(); i.hasNext(); )
        {
            ModelClass modelClass = (ModelClass) i.next();

            JavaClassMetadata javaClassMetadata = (JavaClassMetadata) modelClass.getMetadata( JavaClassMetadata.ID );

            String packageName;

            if ( isPackageWithVersion() )
            {
                packageName = modelClass.getPackageName( true, getGeneratedVersion() );
            }
            else
            {
                packageName = modelClass.getPackageName( false, null );
            }

            char fileSeparator = System.getProperty( "file.separator" ).charAt( 0 );

            String directory = packageName.replace( '.', fileSeparator );

            File f = new File( new File( getOutputDirectory(), directory ), modelClass.getName() + ".java" );

            if ( !f.getParentFile().exists() )
            {
                f.getParentFile().mkdirs();
            }

            FileWriter writer = new FileWriter( f );

            JSourceWriter sourceWriter = new JSourceWriter( writer );

            JClass jClass = new JClass( modelClass.getName() );

            jClass.getJDocComment().setComment( modelClass.getDescription() );

            addModelImports( jClass, modelClass );

            jClass.setPackageName( packageName );

            if ( javaClassMetadata.isAbstract() )
            {
                jClass.getModifiers().setAbstract( true );
            }

            if ( modelClass.getSuperClass() != null )
            {
                jClass.setSuperClass( modelClass.getSuperClass() );
            }

            for ( Iterator j = modelClass.getInterfaces().iterator(); j.hasNext(); )
            {
                String implementedInterface = (String) j.next();

                jClass.addInterface( implementedInterface );
            }

            jClass.addInterface( Serializable.class.getName() );

            for ( Iterator j = modelClass.getFields( getGeneratedVersion() ).iterator(); j.hasNext(); )
            {
                ModelField modelField = (ModelField) j.next();

                if ( modelField instanceof ModelAssociation )
                {
                    createAssociation( jClass, (ModelAssociation) modelField );
                }
                else
                {
                    createField( jClass, modelField );
                }
            }

            // ----------------------------------------------------------------------
            // equals() / hashCode() / toString()
            // ----------------------------------------------------------------------

            List identifierFields = modelClass.getIdentifierFields( getGeneratedVersion() );

            if ( identifierFields.size() != 0 )
            {
                JMethod equals = generateEquals( modelClass );

                jClass.addMethod( equals );

                JMethod hashCode = generateHashCode( modelClass );

                jClass.addMethod( hashCode );

                JMethod toString = generateToString( modelClass );

                jClass.addMethod( toString );
            }

            if ( modelClass.getCodeSegments( getGeneratedVersion() ) != null )
            {
                for ( Iterator iterator = modelClass.getCodeSegments( getGeneratedVersion() ).iterator();
                      iterator.hasNext(); )
                {
                    CodeSegment codeSegment = (CodeSegment) iterator.next();

                    jClass.addSourceCode( codeSegment.getCode() );
                }
            }

            StringBuffer encodingStuff = new StringBuffer();

            encodingStuff.append( "\n    private String modelEncoding = \"UTF-8\";" );
            encodingStuff.append( "\n" );
            encodingStuff.append( "\n    public void setModelEncoding( String modelEncoding )" );
            encodingStuff.append( "\n    {" );
            encodingStuff.append( "\n        this.modelEncoding = modelEncoding;" );
            encodingStuff.append( "\n    }" );
            encodingStuff.append( "\n" );
            encodingStuff.append( "\n    public String getModelEncoding()" );
            encodingStuff.append( "\n    {" );
            encodingStuff.append( "\n        return modelEncoding;" );
            encodingStuff.append( "\n    }" );

            jClass.addSourceCode( encodingStuff.toString() );

            jClass.print( sourceWriter );

            writer.flush();

            writer.close();
        }
    }

    private JMethod generateEquals( ModelClass modelClass )
    {
        JMethod equals = new JMethod( JType.Boolean, "equals" );

        equals.addParameter( new JParameter( new JClass( "Object" ), "other" ) );

        JSourceCode sc = equals.getSourceCode();

        sc.add( "if ( this == other)" );
        sc.add( "{" );
        sc.addIndented( "return true;" );
        sc.add( "}" );
        sc.add( "" );
        sc.add( "if ( !(other instanceof " + modelClass.getName() + ") )" );
        sc.add( "{" );
        sc.addIndented( "return false;" );
        sc.add( "}" );
        sc.add( "" );
        sc.add( modelClass.getName() + " that = (" + modelClass.getName() + ") other;" );
        sc.add( "boolean result = true;" );

        for ( Iterator j = modelClass.getIdentifierFields( getGeneratedVersion() ).iterator(); j.hasNext(); )
        {
            ModelField identifier = (ModelField) j.next();

            String name = identifier.getName();
            if ( "boolean".equals( identifier.getType() ) || "byte".equals( identifier.getType() ) ||
                "char".equals( identifier.getType() ) || "double".equals( identifier.getType() ) ||
                "float".equals( identifier.getType() ) || "int".equals( identifier.getType() ) ||
                "short".equals( identifier.getType() ) || "long".equals( identifier.getType() ) )
            {
                sc.add( "result = result && " + name + "== that." + name + ";" );
            }
            else
            {
                name = "get" + capitalise( name ) + "()";
                sc.add( "result = result && ( " + name + " == null ? that." + name + " == null : " + name +
                    ".equals( that." + name + " ) );" );
            }
        }

        sc.add( "return result;" );

        return equals;
    }

    private JMethod generateToString( ModelClass modelClass )
    {
        JMethod toString = new JMethod( new JType( String.class.getName() ), "toString" );

        List identifierFields = modelClass.getIdentifierFields( getGeneratedVersion() );

        JSourceCode sc = toString.getSourceCode();

        if ( identifierFields.size() == 0 )
        {
            sc.add( "return super.toString();" );

            return toString;
        }

        sc.add( "StringBuffer buf = new StringBuffer();" );

        for ( Iterator j = identifierFields.iterator(); j.hasNext(); )
        {
            ModelField identifier = (ModelField) j.next();

            String getter = "boolean".equals( identifier.getType() ) ? "is" : "get";

            sc.add( "buf.append( \"" + identifier.getName() + " = '\" );" );
            sc.add( "buf.append( " + getter + capitalise( identifier.getName() ) + "()" + " + \"'\" );" );

            if ( j.hasNext() )
            {
                sc.add( "buf.append( \"\\n\" ); " );
            }
        }

        sc.add( "return buf.toString();" );

        return toString;
    }

    private JMethod generateHashCode( ModelClass modelClass )
    {
        JMethod hashCode = new JMethod( JType.Int, "hashCode" );

        List identifierFields = modelClass.getIdentifierFields( getGeneratedVersion() );

        JSourceCode sc = hashCode.getSourceCode();

        if ( identifierFields.size() == 0 )
        {
            sc.add( "return super.hashCode();" );

            return hashCode;
        }

        sc.add( "int result = 17;" );
        sc.add( "long tmp;" );

        for ( Iterator j = identifierFields.iterator(); j.hasNext(); )
        {
            ModelField identifier = (ModelField) j.next();

            if ( "double".equals( identifier.getType() ) )
            {
                sc.add( "tmp = Double.doubleToLongBits( " + identifier.getName() + " );" );
                sc.add( "result = 37 * result + (int) ( tmp ^ ( tmp >>> 32 ) );" );
            }
            else
            {
                sc.add( "result = 37 * result + " + createHashCodeForField( identifier ) + ";" );
            }
        }

        sc.add( "return result;" );

        return hashCode;
    }

    private String createHashCodeForField( ModelField identifier )
    {
        String name = identifier.getName();
        String type = identifier.getType();

        if ( "boolean".equals( type ) )
        {
            return "( " + name + " ? 0 : 1 )";
        }
        else if ( "byte".equals( type ) || "char".equals( type ) || "short".equals( type ) || "int".equals( type ) )
        {
            return "(int) " + name;
        }
        else if ( "long".equals( type ) )
        {
            return "(int) ( " + name + " ^ ( " + name + " >>> 32 ) )";
        }
        else if ( "float".equals( type ) )
        {
            return "Float.floatToIntBits( " + name + " )";
        }
        else if ( "double".equals( type ) )
        {
            return "(int) ( tmp ^ ( tmp >>> 32 ) )";
        }
        else
        {
            return "( " + name + " != null ? " + name + ".hashCode() : 0 )";
        }
    }

    private JField createField( ModelField modelField )
    {
        JType type;

        if ( modelField.getType().equals( "boolean" ) )
        {
            type = JType.Boolean;
        }
        else if ( modelField.getType().equals( "boolean[]" ) )
        {
            type = JType.Boolean.createArray();
        }
        else if ( modelField.getType().equals( "byte" ) )
        {
            type = JType.Byte;
        }
        else if ( modelField.getType().equals( "byte[]" ) )
        {
            type = JType.Byte.createArray();
        }
        else if ( modelField.getType().equals( "char" ) )
        {
            type = JType.Char;
        }
        else if ( modelField.getType().equals( "char[]" ) )
        {
            type = JType.Char.createArray();
        }
        else if ( modelField.getType().equals( "double" ) )
        {
            type = JType.Double;
        }
        else if ( modelField.getType().equals( "double[]" ) )
        {
            type = JType.Double.createArray();
        }
        else if ( modelField.getType().equals( "float" ) )
        {
            type = JType.Float;
        }
        else if ( modelField.getType().equals( "float[]" ) )
        {
            type = JType.Float.createArray();
        }
        else if ( modelField.getType().equals( "int" ) )
        {
            type = JType.Int;
        }
        else if ( modelField.getType().equals( "int[]" ) )
        {
            type = JType.Int.createArray();
        }
        else if ( modelField.getType().equals( "short" ) )
        {
            type = JType.Short;
        }
        else if ( modelField.getType().equals( "short[]" ) )
        {
            type = JType.Short.createArray();
        }
        else if ( modelField.getType().equals( "long" ) )
        {
            type = JType.Long;
        }
        else if ( modelField.getType().equals( "long[]" ) )
        {
            type = JType.Long.createArray();
        }
        else if ( modelField.getType().equals( "DOM" ) )
        {
            // TODO: maybe DOM is not how to specify it in the model, but just Object and markup Xpp3Dom for the Xpp3Reader?
            //   not usre how we'll treat it for the other sources, eg sql.
            type = new JClass( "Object" );
        }
        else if ( modelField.getType().equals( "DOM[]" ) )
        {
            type = new JClass( "Object" ).createArray();
        }
        else if ( modelField.isArray() )
        {
            type = new JClass( modelField.getType() ).createArray();
            }
        else
        {
            type = new JClass( modelField.getType() );
        }

        JField field = new JField( type, modelField.getName() );

        if ( modelField.getDefaultValue() != null )
        {
            if ( modelField.getType().equals( "String" ) )
            {
                field.setInitString( "\"" + modelField.getDefaultValue() + "\"" );
            }
            else
            {
                field.setInitString( modelField.getDefaultValue() );
            }
        }

        return field;
    }

    private void createField( JClass jClass, ModelField modelField )
    {
        JavaFieldMetadata javaFieldMetadata = (JavaFieldMetadata) modelField.getMetadata( JavaFieldMetadata.ID );

        JField field = createField( modelField );

        jClass.addField( field );

        if ( javaFieldMetadata.isGetter() )
        {
            jClass.addMethod( createGetter( field, modelField ) );
        }

        if ( javaFieldMetadata.isSetter() )
        {
            jClass.addMethod( createSetter( field, modelField ) );
        }
    }

    private JMethod createGetter( JField field, ModelField modelField )
    {
        String propertyName = capitalise( field.getName() );

        JavaFieldMetadata javaFieldMetadata = (JavaFieldMetadata) modelField.getMetadata( JavaFieldMetadata.ID );

        String prefix = javaFieldMetadata.isBooleanGetter() ? "is" : "get";

        JMethod getter = new JMethod( field.getType(), prefix + propertyName );

        getter.getJDocComment().setComment( "Get " + modelField.getDescription() );

        getter.getSourceCode().add( "return this." + field.getName() + ";" );

        return getter;
    }

    private JMethod createSetter( JField field, ModelField modelField )
    {
        String propertyName = capitalise( field.getName() );

        JMethod setter = new JMethod( null, "set" + propertyName );

        setter.getJDocComment().setComment( "Set " + modelField.getDescription() );

        setter.addParameter( new JParameter( field.getType(), field.getName() ) );

        JSourceCode sc = setter.getSourceCode();

        if ( modelField instanceof ModelAssociation && isBidirectionalAssociation( (ModelAssociation) modelField ) &&
            ModelAssociation.ONE_MULTIPLICITY.equals( ( (ModelAssociation) modelField ).getMultiplicity() ) )
        {
            ModelAssociation modelAssociation = (ModelAssociation) modelField;

            // TODO: remove after tested
//            setter.addException( new JClass( "Exception" ) );

            sc.add( "if ( this." + field.getName() + " != null )" );

            sc.add( "{" );

            sc.indent();

            sc.add( "this." + field.getName() + ".break" + modelAssociation.getModelClass().getName() +
                "Association( this );" );

            sc.unindent();

            sc.add( "}" );

            sc.add( "" );

            sc.add( "this." + field.getName() + " = " + field.getName() + ";" );

            sc.add( "" );

            sc.add( "if ( " + field.getName() + " != null )" );

            sc.add( "{" );

            sc.indent();

            sc.add( "this." + field.getName() + ".create" + modelAssociation.getModelClass().getName() +
                "Association( this );" );

            sc.unindent();

            sc.add( "}" );
        }
        else
        {
            sc.add( "this." + field.getName() + " = " + field.getName() + ";" );
        }

        return setter;
    }

    private void createAssociation( JClass jClass, ModelAssociation modelAssociation )
    {
        JavaFieldMetadata javaFieldMetadata = (JavaFieldMetadata) modelAssociation.getMetadata( JavaFieldMetadata.ID );

        if ( ModelAssociation.MANY_MULTIPLICITY.equals( modelAssociation.getMultiplicity() ) )
        {
            JType type = new JClass( modelAssociation.getType() );

            JField jField = new JField( type, modelAssociation.getName() );

            if ( !isEmpty( modelAssociation.getComment() ) )
            {
                jField.setComment( modelAssociation.getComment() );
            }

            // TODO: if the association field isn't lazy initialized set the
            // default
            //            jField.setInitString( modelAssociation.getDefaultValue() );

            jClass.addField( jField );

            if ( javaFieldMetadata.isGetter() )
            {
                // TODO: If the association field isn't lazy initialized create
                // a normal getter
                String propertyName = capitalise( jField.getName() );

                JMethod getter = new JMethod( jField.getType(), "get" + propertyName );

                JSourceCode sc = getter.getSourceCode();

                sc.add( "if ( this." + jField.getName() + " == null )" );

                sc.add( "{" );

                sc.indent();

                sc.add( "this." + jField.getName() + " = " + modelAssociation.getDefaultValue() + ";" );

                sc.unindent();

                sc.add( "}" );

                sc.add( "" );

                sc.add( "return this." + jField.getName() + ";" );

                jClass.addMethod( getter );
            }

            if ( javaFieldMetadata.isSetter() )
            {
                jClass.addMethod( createSetter( jField, modelAssociation ) );
            }

            if ( javaFieldMetadata.isAdder() )
            {
                createAdder( modelAssociation, jClass );
            }
        }
        else
        {
            createField( jClass, modelAssociation );
        }

        if ( isBidirectionalAssociation( modelAssociation ) )
        {
            JMethod createMethod = new JMethod( null, "create" + modelAssociation.getTo() + "Association" );

            createMethod.addParameter(
                new JParameter( new JClass( modelAssociation.getTo() ), uncapitalise( modelAssociation.getTo() ) ) );

            // TODO: remove after tested
//            createMethod.addException( new JClass( "Exception" ) );

            JSourceCode sc = createMethod.getSourceCode();

            if ( ModelAssociation.ONE_MULTIPLICITY.equals( modelAssociation.getMultiplicity() ) )
            {
                sc.add( "if ( this." + modelAssociation.getName() + " != null )" );

                sc.add( "{" );

                sc.indent();

                sc.add(
                    "break" + modelAssociation.getTo() + "Association( this." + modelAssociation.getName() + " );" );

                sc.unindent();

                sc.add( "}" );

                sc.add( "" );

                sc.add( "this." + modelAssociation.getName() + " = " + uncapitalise( modelAssociation.getTo() ) + ";" );
            }
            else
            {
                jClass.addImport( "java.util.Collection" );

                sc.add( "Collection " + modelAssociation.getName() + " = get" +
                    capitalise( modelAssociation.getName() ) + "();" );

                sc.add( "" );

                sc.add( "if ( get" + capitalise( modelAssociation.getName() ) + "().contains(" +
                    uncapitalise( modelAssociation.getTo() ) + ") )" );

                sc.add( "{" );

                sc.indent();

                sc.add( "throw new IllegalStateException( \"" + uncapitalise( modelAssociation.getTo() ) +
                    " is already assigned.\" );" );

                sc.unindent();

                sc.add( "}" );

                sc.add( "" );

                sc.add( modelAssociation.getName() + ".add( " + uncapitalise( modelAssociation.getTo() ) + " );" );
            }

            jClass.addMethod( createMethod );

            JMethod breakMethod = new JMethod( null, "break" + modelAssociation.getTo() + "Association" );

            breakMethod.addParameter(
                new JParameter( new JClass( modelAssociation.getTo() ), uncapitalise( modelAssociation.getTo() ) ) );

            // TODO: remove after tested
//            breakMethod.addException( new JClass( "Exception" ) );

            sc = breakMethod.getSourceCode();

            if ( ModelAssociation.ONE_MULTIPLICITY.equals( modelAssociation.getMultiplicity() ) )
            {
                sc.add( "if ( this." + modelAssociation.getName() + " != " + uncapitalise( modelAssociation.getTo() ) +
                    " )" );

                sc.add( "{" );

                sc.indent();

                sc.add( "throw new IllegalStateException( \"" + uncapitalise( modelAssociation.getTo() ) +
                    " isn't associated.\" );" );

                sc.unindent();

                sc.add( "}" );

                sc.add( "" );

                sc.add( "this." + modelAssociation.getName() + " = null;" );
            }
            else
            {
                sc.add( "if ( ! get" + capitalise( modelAssociation.getName() ) + "().contains( " +
                    uncapitalise( modelAssociation.getTo() ) + " ) )" );

                sc.add( "{" );

                sc.indent();

                sc.add( "throw new IllegalStateException( \"" + uncapitalise( modelAssociation.getTo() ) +
                    " isn't associated.\" );" );

                sc.unindent();

                sc.add( "}" );

                sc.add( "" );

                sc.add( "get" + capitalise( modelAssociation.getName() ) + "().remove( " +
                    uncapitalise( modelAssociation.getTo() ) + " );" );
            }

            jClass.addMethod( breakMethod );
        }
    }

    private void createAdder( ModelAssociation modelAssociation, JClass jClass )
    {
        String fieldName = modelAssociation.getName();

        String parameterName = uncapitalise( modelAssociation.getTo() );

        boolean bidirectionalAssociation = isBidirectionalAssociation( modelAssociation );

        JType addType;

        if ( modelAssociation.getToClass() != null )
        {
            addType = new JClass( modelAssociation.getToClass().getName() );
        }
        else
        {
            addType = new JClass( "String" );
        }

        if ( modelAssociation.getType().equals( ModelDefault.PROPERTIES ) ||
            modelAssociation.getType().equals( ModelDefault.MAP ) )
        {
            JMethod adder = new JMethod( null, "add" + capitalise( singular( fieldName ) ) );

            if ( modelAssociation.getType().equals( ModelDefault.MAP ) )
            {
                adder.addParameter( new JParameter( new JClass( "Object" ), "key" ) );
            }
            else
            {
                adder.addParameter( new JParameter( new JClass( "String" ), "key" ) );
            }

            adder.addParameter( new JParameter( new JClass( modelAssociation.getTo() ), "value" ) );

            adder.getSourceCode().add( "get" + capitalise( fieldName ) + "().put( key, value );" );

            jClass.addMethod( adder );
        }
        else
        {
            JMethod adder = new JMethod( null, "add" + singular( capitalise( fieldName ) ) );

            adder.addParameter( new JParameter( addType, parameterName ) );

            adder.getSourceCode().add( "get" + capitalise( fieldName ) + "().add( " + parameterName + " );" );

            if ( bidirectionalAssociation )
            {
                // TODO: remove after tested
//                adder.addException( new JClass( "Exception" ) );

                adder.getSourceCode().add(
                    parameterName + ".create" + modelAssociation.getModelClass().getName() + "Association( this );" );
            }

            jClass.addMethod( adder );

            JMethod remover = new JMethod( null, "remove" + singular( capitalise( fieldName ) ) );

            remover.addParameter( new JParameter( addType, parameterName ) );

            if ( bidirectionalAssociation )
            {
                // TODO: remove after tested
//                remover.addException( new JClass( "Exception" ) );

                remover.getSourceCode().add(
                    parameterName + ".break" + modelAssociation.getModelClass().getName() + "Association( this );" );
            }

            remover.getSourceCode().add( "get" + capitalise( fieldName ) + "().remove( " + parameterName + " );" );

            jClass.addMethod( remover );
        }
    }

    private boolean isBidirectionalAssociation( ModelAssociation association )
    {
        Model model = association.getModelClass().getModel();

        if ( !isClassInModel( association.getTo(), model ) )
        {
            return false;
        }

        ModelClass toClass = association.getToClass();

        for ( Iterator j = toClass.getFields( getGeneratedVersion() ).iterator(); j.hasNext(); )
        {
            ModelField modelField = (ModelField) j.next();

            if ( !( modelField instanceof ModelAssociation ) )
            {
                continue;
            }

            ModelAssociation modelAssociation = (ModelAssociation) modelField;

            if ( !isClassInModel( modelAssociation.getTo(), model ) )
            {
                continue;
            }

            ModelClass totoClass = modelAssociation.getToClass();

            if ( association.getModelClass().equals( totoClass ) )
            {
                return true;
            }
        }

        return false;
    }
}