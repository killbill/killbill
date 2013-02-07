Testing the JRuby OSGI bridge
-----------------------------

1. Build the jruby OSGI module and copy the bundle to:

        /var/tmp/killbill-osgi-bundles-jruby-*-jar-with-dependencies.jar 

2. Build a ruby plugin, e.g. killbill-paypal-express:

        rake killbill:clean
        rake build
        rake killbill:package

3. Copy the built tree to the bundles directory:

        mkdir -p /var/tmp/bundles/ruby
        mv pkg/killbill-paypal-express-*/killbill-paypal-express /var/tmp/bundles/ruby/
