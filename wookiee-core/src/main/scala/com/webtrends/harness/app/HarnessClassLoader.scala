/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.app

import java.io.InputStream
import java.net._
import com.webtrends.harness.service.ServiceClassLoader
import scala.util.{Failure, Success, Try}

class HarnessClassLoader(parent: ClassLoader) extends URLClassLoader(Array.empty[URL], parent) {

  private var childLoaders: Seq[ServiceClassLoader] = Nil

  /**
   * Adds a sequence of urls to load into this class loader
   * @param urls
   */
  def addURLs(urls: Seq[URL]) = urls foreach {
    addURL(_)
  }

  /**
   * Add the child service loader so it can be used to search for classes in child loaders
   * @param loader an instance of ServiceClassLoader
   */
  def addChildLoader(loader: ServiceClassLoader) = childLoaders = childLoaders ++ Seq(loader)

  /**
   * ClassLoader overrides
   */
  override def loadClass(name: String, resolve: Boolean): Class[_] = {
    // First, check if the class has already been loaded
    Try(super.loadClass(name, resolve)) match {
      case Success(v) => v
      case Failure(_) => loadClassFromChildren(name, resolve) getOrElse (throw new ClassNotFoundException("Could not locate the class " + name))
    }
  }

  override def getResource(name: String): URL = {
    Try(super.getResource(name)) match {
      case Success(v) if v != null => v
      case _ => getResourceFromChildren(name)
    }
  }

  override def getResources(name: String): java.util.Enumeration[URL] = {
    super.getResources(name)
  }

  override def getResourceAsStream(name: String): InputStream = {
    super.getResourceAsStream(name)
    //    val url: URL = getResource(name)
    //
    //    Try(
    //      {if (url == null) {
    //        null
    //      }
    //      val urlc = url.openConnection
    //      val is = urlc.getInputStream
    //      if (urlc.isInstanceOf[JarURLConnection]) {
    //        val juc = urlc.asInstanceOf[JarURLConnection]
    //        val jar = juc.getJarFile
    //        closeables synchronized {
    //          if (!closeables.containsKey(jar)) {
    //            closeables.put(jar, null)
    //          }
    //        }
    //      }
    //      else if (urlc.isInstanceOf[FileURLConnection]) {
    //        closeables synchronized {
    //          closeables.put(is, null)
    //        }
    //      }
    //      return is
    //    }) match {
    //      case Success(v) if v != null => v
    //      case _ => getResourceAsStreamFromChildren(name)
    //    }
  }

  private def loadClassFromChildren(name: String, resolve: Boolean): Option[Class[_]] = {
    if (childLoaders.isEmpty) {
      None
    }
    else {
      this.synchronized {
        // Get the loaded class
        childLoaders.filterNot(_.getLoadedClass(name) == None) match {
          case Nil =>
            var ret: Option[Class[_]] = None
            for (value <- childLoaders; if ret == None) {
              ret = value.loadClassLocally(name, resolve)
            }
            ret
          case list => // Return the first if already loaded
            list.headOption.get.getLoadedClass(name)
        }
      }
    }
  }

  private def getResourceFromChildren(name: String): URL = {
    if (childLoaders.isEmpty) {
      null
    }
    else {
      (for {
        value <- childLoaders
        url = value.findResource(name)
        if (url != null)
      } yield url).headOption match {
        case Some(url) => url
        case None => null
      }
    }
  }

  private def getResourcesFromChildren(name: String): java.util.Enumeration[URL] = {
    Try(super.getResources(name)).get
  }

  private def getResourceAsStreamFromChildren(name: String): InputStream = {
    if (childLoaders.isEmpty) {
      null
    }
    else {
      (for {
        value <- childLoaders
        url = value.getResourceAsStream(name)
        if (url != null)
      } yield url).headOption match {
        case Some(url) => url
        case None => null
      }
    }
  }

}

object HarnessClassLoader {
  def apply(parent: ClassLoader) = new HarnessClassLoader(parent)
}
