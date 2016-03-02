/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.web.guice;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;

import javax.ws.rs.WebApplicationException;

import org.jboss.resteasy.core.InjectorFactoryImpl;
import org.jboss.resteasy.core.ValueInjector;
import org.jboss.resteasy.spi.ApplicationException;
import org.jboss.resteasy.spi.ConstructorInjector;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.InjectorFactory;
import org.jboss.resteasy.spi.MethodInjector;
import org.jboss.resteasy.spi.PropertyInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.metadata.Parameter;
import org.jboss.resteasy.spi.metadata.ResourceClass;
import org.jboss.resteasy.spi.metadata.ResourceConstructor;
import org.jboss.resteasy.spi.metadata.ResourceLocator;

import com.google.inject.Injector;

@SuppressWarnings("rawtypes")
public class GuiceInjectorFactory implements InjectorFactory {
  private final InjectorFactory delegate = new InjectorFactoryImpl();
  private final Injector injector;

  public GuiceInjectorFactory(Injector injector) {
    this.injector = injector;
  }

  @Override
  public ConstructorInjector createConstructor(Constructor constructor, ResteasyProviderFactory factory) {
    return new GuiceConstructorInjector(constructor, delegate.createConstructor(constructor, factory));
  }

  @Override
  public PropertyInjector createPropertyInjector(Class resourceClass, ResteasyProviderFactory factory) {
    return delegate.createPropertyInjector(resourceClass, factory);
  }

  @Override
  public ValueInjector createParameterExtractor(Class injectTargetClass, AccessibleObject injectTarget, Class type, Type genericType, Annotation[] annotations, ResteasyProviderFactory factory) {
    return delegate.createParameterExtractor(injectTargetClass, injectTarget, type, genericType, annotations, factory);
  }

  @Override
  public ValueInjector createParameterExtractor(Class injectTargetClass, AccessibleObject injectTarget, Class type, Type genericType, Annotation[] annotations, boolean useDefault, ResteasyProviderFactory factory) {
    return delegate.createParameterExtractor(injectTargetClass, injectTarget, type, genericType, annotations, useDefault, factory);
  }

  @Override
  public ValueInjector createParameterExtractor(Parameter parameter, ResteasyProviderFactory providerFactory) {
    return delegate.createParameterExtractor(parameter, providerFactory);
  }

  @Override
  public MethodInjector createMethodInjector(ResourceLocator method, ResteasyProviderFactory factory) {
    return delegate.createMethodInjector(method, factory);
  }

  @Override
  public PropertyInjector createPropertyInjector(ResourceClass resourceClass, ResteasyProviderFactory providerFactory) {
    return delegate.createPropertyInjector(resourceClass, providerFactory);
  }

  @Override
  public ConstructorInjector createConstructor(ResourceConstructor constructor, ResteasyProviderFactory providerFactory) {
    return new GuiceConstructorInjector(constructor.getConstructor(), delegate.createConstructor(constructor, providerFactory));
  }

  private class GuiceConstructorInjector implements ConstructorInjector {
    private final Constructor constructor;
    private final ConstructorInjector delegate;

    public GuiceConstructorInjector(Constructor constructor, ConstructorInjector delegate) {
      this.constructor = constructor;
      this.delegate = delegate;
    }

    @Override
    public Object construct() {
      Object instance = delegate.construct();
      injector.injectMembers(instance);
      return instance;
    }

    @Override
    public Object construct(HttpRequest request, HttpResponse response) throws Failure, WebApplicationException, ApplicationException {
      Object instance = delegate.construct(request, response);
      injector.injectMembers(instance);
      return instance;
    }

    @Override
    public Object[] injectableArguments() {
      return delegate.injectableArguments();
    }

    @Override
    public Object[] injectableArguments(HttpRequest request, HttpResponse response) throws Failure {
      return delegate.injectableArguments(request, response);
    }
  }
}
