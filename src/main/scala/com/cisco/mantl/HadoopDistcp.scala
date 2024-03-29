package com.cisco.mantl

import java.io.{FileInputStream, InputStream}
import java.util.Properties

import com.cisco.extensions.SwiftExtension
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.mapred.JobConf

/**
 * Command line runner, the starting point to run anything
 */
object HadoopDistcp extends App {

  override def main(args: Array[String]) {

    val parser = new scopt.OptionParser[CliOptions]("--") {
      head("Hadoop DistCp", "0.x")

      opt[String]("swiftEndpoint") action { (x, c) => c.copy(swiftEndpoint = x) } text ("Swift endpoint. Ex.:https://us-texas-3.cloud:5000/v2.0")
      opt[String]("swiftUsername") action { (x, c) => c.copy(swiftUsername = x) } text ("Username")
      opt[String]("swiftPassword") action { (x, c) => c.copy(swiftPassword = x) } text ("Password")
      opt[String]("swiftTenant") action { (x, c) => c.copy(swiftTenant = x) } text ("Tenant")

      opt[String]("swiftConf") action { (x, c) => c.copy(swiftConf = x) } text ("config file: /etc/swift.conf")

      opt[String]('m', "mode") required() valueName ("fromSwiftToHdfs|fromHdfsToSwift|fromHdfsToHdfs") action { (x, c) => c.copy(mode = x) } text ("execution mode: what to do")
      opt[String]("swiftContainer") action { (x, c) => c.copy(swiftContainer = x) } text ("swift container")
      opt[String]("swiftUri") action { (x, c) => c.copy(swiftUri = x) } text ("swift object")
      opt[String]("hdfsUri") action { (x, c) => c.copy(hdfsUri = x) } text ("HDFS path")
      opt[String]("hdfsSrc") action { (x, c) => c.copy(hdfsSrc = x) } text ("HDFS src file")
      opt[String]("hdfsDst") action { (x, c) => c.copy(hdfsDst = x) } text ("HDFS dst file")
    }


    parser.parse(args, CliOptions()) match {
      case Some(config) => {
        val hadoopConf = new JobConf(this.getClass)
          .setJobName("Hadoop DistCp")

        val properties: Properties = new Properties()
        properties.setProperty("swift.debug", "false")

        val confFile: FileSystem = FileSystem.get(new Configuration(true))

        if (confFile.isFile(new Path(config.swiftConf))) {
          val in: InputStream = confFile.open(new Path(config.swiftConf))
          properties.load(in)
        } else if (new java.io.File(config.swiftConf).exists) {
          val in: InputStream = new FileInputStream(config.swiftConf)
          properties.load(in)
        } else {
          println("Configuration swift file could not be read so we are going to use the default properties!")
        }

        if(!config.swiftEndpoint.isEmpty) properties.setProperty("swift.endpoint", config.swiftEndpoint)
        if(!config.swiftUsername.isEmpty) properties.setProperty("swift.username", config.swiftUsername)
        if(!config.swiftPassword.isEmpty) properties.setProperty("swift.password", config.swiftPassword)
        if(!config.swiftTenant.isEmpty) properties.setProperty("swift.tenant", config.swiftTenant)

        println(properties.getProperty("swift.endpoint"))

        config.mode match {
          case "fromSwiftToHdfs" => {
            try {
              val swiftExtension = new SwiftExtension(properties)
              swiftExtension.fromSwiftToHdfsWithRecursion(config.swiftContainer, config.swiftUri, config.hdfsUri)
            } catch {
              case e: Exception => println("Problem with copying from Swift to Hdfs. Invalid login details to Swift may also cause this issue. Details: " + e)
            } finally {
              println("Done")
            }

          }
          case "fromHdfsToSwift" => {
            try {
              val swiftExtension = new SwiftExtension(properties)
              swiftExtension.fromHdfsToSwiftWithRecursion(config.hdfsUri, config.swiftContainer, config.swiftUri)
            } catch {
              case e: Exception => println("Problem with copying from Hdfs to Swift. Invalid login details to Swift may also cause this issue. Details: " + e)
            } finally {
              println("Done")
            }

          }
          case "fromHdfsToHdfs" => {
            try {
              val swiftExtension = new SwiftExtension(properties)
              swiftExtension.copyOnHdfs(config.hdfsSrc, config.hdfsDst)
            } catch {
              case e: Exception => println("Problem with copying from Hdfs to Hdfs. Invalid login details to Swift may also cause this issue. Details: " + e)
            } finally {
              println("Done")
            }
          }
          case _ => println("Unknown command " + config.mode + ", expected one of fromSwiftToHdfs|fromHdfsToSwift|fromHdfsToHdfs");
        }
      }
      case None =>
    }
  }

}