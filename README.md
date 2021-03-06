
# TextUp Backend

TextUp Grails backend

| Service | Master | Dev |
| --- | --- | --- |
| CI Status | [![Build Status](https://travis-ci.org/TextUp/textup-backend.svg?branch=master)](https://travis-ci.org/TextUp/textup-backend) | [![Build Status](https://travis-ci.org/TextUp/textup-backend.svg?branch=dev)](https://travis-ci.org/TextUp/textup-backend) |

## Installation

### Git Large File Storage

We version control static compiled binaries in the `vendor/` folder using [Git LFS](https://git-lfs.github.com/). See the Git LFS website for instructions on how to install.

### Using Grails wrapper

* Make sure the `grailsw` is executable: `chmod +x grailsw`
* For all following commands, replace `grails` with `./grailsw`

### Using SDKMAN!

* [Install SDKMAN!](http://sdkman.io/install.html)
* `sdk install grails 2.4.4`

## Adding Let's Encrypt certificates

Depending on which version of the Java SDK you are using, you may need to manually add the certificates for the Let's Encrypt certificate authority to the keystore. Some of the external Maven repos we pull dependencies from may use certificates from Let's Encrypt and we need to be able to successfully resolve the SSH connections in order to install key dependencies.

If needed, a script to automatically pull and install the Let's Encrypt certificates is located at `.install/add-lets-encrypt-certs.sh`. Before running the script, make sure that the `$JAVA_HOME` environment variable is set and that `wget` is installed. Afterwards, this script can be run as such: `.install/add-lets-encrypt-certs.sh $JAVA_HOME`

## Databases

Both development and testing environments use an in-memory H2 database.

Staging and production environments use a MySQL v5.6 database. In order for the app to successfully start up, the appropriate user must be created with full permissions on the production database. Check `DataSource.groovy` to see the name of the production database.

Make sure to pass in the correct username and password values for the database user you create with access to the production database. See the following section on environment variables for the names of the properties you may use to pass in the database username and password.

## Environment variables

In order to successfully run, certain environment variables are required, accessed primarily in `grails-app/conf/BuildConfig.groovy`. Below, we outline variables required for each environment with typical values where appropriate.

**NOTE**: if you are changing `ENABLE_QUARTZ` to true on the staging or development environments, make sure to double check that Quartz has no triggers that will fire. Triggers that fire immediately when past due will cause all those outdated scheduled messages to erroneously send to possibly many unintended recipients.

### For all environments: JavaMelody monitoring

Storage directory must be hardcoded in `grails-app/conf/GrailsMelodyConfig.groovy` because this value fixed when the WAR file is built. **Therefore, the app requires the folder `/grails-monitoring`.**

Make sure user and group for this folder are both `tomcat7`: `sudo chown -R tomcat7:tomcat7 /grails-monitoring`

### Shared environment variables

* Note that `TEXTUP_BACKEND_URL_NOTIFY_MESSAGE` and `TEXTUP_BACKEND_URL_PASSWORD_RESET` will not have a trailing slash appended because they expect that the URL will end in a query parameter
* See any of the files in `.travis/env-variables/` for a comprehensive list of environment variables. These environment variables used in [staging](https://dev.textup.org) and [production](https://v2.textup.org) are common to all environments with the following exceptions grouped by environment:

#### Development (local)

* Do not need `TEXTUP_BACKEND_SERVER_URL`

#### [Testing](https://travis-ci.org/TextUp/textup-backend)

* Do not need `TEXTUP_BACKEND_SERVER_URL`
* `TEXTUP_BACKEND_CDN_PRIVATE_KEY_PATH`: should be `$TRAVIS_BUILD_DIR/.travis/test.pem`

#### Deployment

These environment variables are used for deployment by [Travis CI](https://travis-ci.org/TextUp/textup-backend).

* `DEPLOY_HOSTNAME_PRODUCTION`: should be an ip address
* `DEPLOY_HOSTNAME_STAGING`: should be an ip address
* `DEPLOY_USER_PRODUCTION`: username of production machine
* `DEPLOY_USER_STAGING`: username of staging machine

## Running / Development

### Development

* `grails run-app`

### Running Tests

Running the entire test suite may lead to an insufficient PermGen space error. Therefore, we split up running the test suite to avoid this issue.

See the `script` section of `.travis.yml` for the tests. Note that the command is `grails` instead of `./grailsw` if you installed via SDKMAN! and are not using the wrapper.

### Building for deployment

* `grails prod war`

### Deploying

#### Deploying to staging

* Push to the `dev` branch to trigger a Travis CI build OR
* Deploy manually
    * Copy the generated WAR file to the appropriate server using `scp`
    * Copy the generated WAR file to the appropriate location on the server and restart the Tomcat server. See `.travis/remote.sh` for the steps.

#### Deploying to production

* Push to the `master` branch to trigger a Travis CI build OR
* Deploy manually (same steps as for the staging environment)

## Useful links

* [Grails 2.4.4 documentation](https://grails.github.io/grails2-doc/2.4.4/index.html)

## Development tips

* If you are seeing the `grails` command hang and are using an institution's wifi, it may be because you are behind a proxy. Try running `grails` commands with the `--offline` flag.
* For classes with the Validateable annotation, public getters with no properties and no defined field are treated like fields during validation. Making these getters protected or overloading the method stops these from being treated as constrainted properties. Therefore, in this special case, if we don't want these methods to be called during validation, we need to (1) rename the method, (2) make the method protected, or (3) overload the method. If we are all right with the getter being called but we want to apply custom constraints on it, then we need to declare it as a static final field to make the constraints pass type checking.
* Run all tests from command line before sending to CI via:
```shell
{ grails --offline test-app "unit:" org.textup.action.* org.textup.cache.* org.textup.constraint.* org.textup.job.* org.textup.override.* org.textup.test.* org.textup.type.* org.textup.media.* ; grails --offline test-app "unit:" org.textup.util.* org.textup.util.domain.* ; grails --offline test-app "unit:" org.textup.rest.* ; grails --offline test-app "unit:" org.textup.validator.* org.textup.validator.action.* ; grails --offline test-app "unit:" org.textup.A* org.textup.B* org.textup.C* org.textup.D* org.textup.E* org.textup.F* org.textup.G* org.textup.H* org.textup.I* org.textup.J* org.textup.K* org.textup.L* org.textup.M* org.textup.N* org.textup.O* ; grails --offline test-app "unit:" org.textup.P* org.textup.Q* org.textup.R* org.textup.S* org.textup.T* org.textup.U* org.textup.V* org.textup.W* org.textup.X* org.textup.Y* org.textup.Z* ; grails --offline test-app "integration:" org.textup.action.* org.textup.annotation.* org.textup.override.* org.textup.util.* org.textup.rest.marshaller.* ; grails --offline test-app "integration:" org.textup.* ; grails --offline test-app "functional:" }  > test-output.txt
```
* If possible, don't import our own classes into config files because no type-checking happens in these config files. A "class not found error" can silently fail and cause the config file to not be read.
* The `@Sortable` annotation on Grails domain classes seems to trigger an errors duration in the canonicalization phase of compilation when used with the `@GrailsTypeChecked` annotation. Need to manually implement the `Comparable` interface. `@Sortable` seems to be fine when applied to `@Validateable` classes
* No static inner classes when you apply `@GrailsTypeChecked` to a validateable class or else will have canonicalization compilation errors. This includes both domain classes and classes with the `@Validateable` annotation.

## License

Copyright 2019 TextUp

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

---

This project also uses static builds of FFmpeg as libraries for audio processing. FFmpeg is licensed under LGPLv3, but the statically compiled version we use is licensed under GPLv3 because of included optional codecs. See [the FFmpeg website](http://ffmpeg.org/legal.html) for more details on FFmpeg licensing. See the license documents in `vendor/ffmpeg-4.0.2/` for additional information on licensing for the FFmpeg static builds we use.
