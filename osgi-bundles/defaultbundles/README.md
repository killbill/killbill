Killbill default OSGI bundles
-----------------------------

This module is simply used as a build utility. It produces a .tar.gz
artifact containing various useful OSGI bundles one may want to have
available by default.

For example, to install these default bundles with the start-server script, unpack
the .tar.gz artifact under the *server/load* directory.

Killbill uses the Felix fileinstall bundle to detect bundles to install, see [here](http://felix.apache.org/documentation/subprojects/apache-felix-file-install.html)
for more information.