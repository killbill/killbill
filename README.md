## Kill Bill

Kill Bill is the Open-Source Billing & Payment Platform.

## Among features

* Subscription engine, with plans management (trial, upgrade, downgrade, etc.), support of add-ons, bundles with multiple subscriptions
* Invoicing engine, supporting different billing alignments, recurring and one-time charges, international tax, metered billing
* Payment state machine, with payment routing capabilities, supporting dozen of gateways
* Plugin architecture, which allows further customization with your own business logic, in Java or Ruby

You can find more information on [killbill.io](http://killbill.io).

## Getting started

* [Tutorials](http://killbill.io/tutorials/)
* [User guides](http://killbill.io/userguide/) (source in the [killbill-docs](https://github.com/killbill/killbill-docs) repo)
* [Wiki](https://github.com/killbill/killbill/wiki)

## Build

Build is handled by Maven:

```
mvn clean install -DskipTests=true
```

Note: some third-party artifacts (such as metrics-guice) are released in Bintray. Make sure to follow the instructions [here](https://bintray.com/bintray/jcenter) (Set me up! button) to update your settings.xml.

## License

Kill Bill is released under the [Apache license](http://www.apache.org/licenses/LICENSE-2.0).
