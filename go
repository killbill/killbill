#!/System/Library/Frameworks/Ruby.framework/Versions/1.8/usr/bin/ruby
#
# Killbill quick installer, inspired by the awesome Homebrew installer
#

KILLBILL_MYSQL_DATABASE = 'killbill'
KILLBILL_MYSQL_USER = 'killbill'
KILLBILL_MYSQL_PASSWORD = 'killbill'

module Tty extend self
  def blue; bold 34; end
  def white; bold 39; end
  def red; underline 31; end
  def reset; escape 0; end
  def bold n; escape "1;#{n}" end
  def underline n; escape "4;#{n}" end
  def escape n; "\033[#{n}m" if STDOUT.tty? end
end

class Array
  def shell_s
    cp = dup
    first = cp.shift
    cp.map{ |arg| arg.gsub " ", "\\ " }.unshift(first) * " "
  end
end

def ohai *args
  puts "#{Tty.blue}==>#{Tty.white} #{args.shell_s}#{Tty.reset}"
end

def warn warning
  puts "#{Tty.red}Warning#{Tty.reset}: #{warning.chomp}"
end

def system *args
  abort "Failed during: #{args.shell_s}" unless Kernel.system *args
end

def find_cmd(cmd)
  name = cmd.upcase
  if ENV[name] and File.executable? ENV[name]
    ENV[name]
  elsif Kernel.system "/usr/bin/which -s #{cmd}"
    cmd
  else
    s = `xcrun -find #{cmd} 2>/dev/null`.chomp
    s if $? and not s.empty?
  end
end

def mysql
  @mysql ||= find_cmd('mysql')
end

def curl
  @curl ||= find_cmd('curl')
end

def get(url)
  %x[#{curl} -skSfL #{url}]
end

####################################################################### script
abort "Don't run this as root!" if Process.uid == 0

# TODO Pierre versioned schema!
ohai "Downloading the latest DDL schema from github..."
ddl = (get "http://killbilling.org/schema")

ohai "Creating MySQL database #{KILLBILL_MYSQL_DATABASE} and user #{KILLBILL_MYSQL_USER} with password #{KILLBILL_MYSQL_PASSWORD}... Enter your MySQL root password when prompted"
system mysql, "-u", "root", "-p", "-e", <<CMD
create database #{KILLBILL_MYSQL_DATABASE};
create user #{KILLBILL_MYSQL_USER};
grant all on #{KILLBILL_MYSQL_DATABASE}.* to #{KILLBILL_MYSQL_USER}@localhost identified by '#{KILLBILL_MYSQL_PASSWORD}';
use #{KILLBILL_MYSQL_DATABASE};
#{ddl}
CMD

maven_metadata = (get "http://search.maven.org/solrsearch/select?q=g:%22com.ning.billing%22%20AND%20a:%22killbill-server%22%20AND%20p:%22war%22&rows=20&wt=json")
# TODO Pierre required? Could we work around it?
require 'json'
latest_version = JSON.parse(maven_metadata)["response"]["docs"][0]["latestVersion"]
killbill_war = "killbill-server-#{latest_version}-jetty-console.war"

ohai "Downloading #{killbill_war}..."
system curl, "-O", "http://search.maven.org/remotecontent?filepath=com/ning/billing/killbill-server/#{latest_version}/#{killbill_war}"

props = {
  "com.ning.jetty.jdbi.url" => "jdbc:mysql://127.0.0.1:3306/#{KILLBILL_MYSQL_DATABASE}",
  "com.ning.jetty.jdbi.user" => KILLBILL_MYSQL_USER,
  "com.ning.jetty.jdbi.password" => KILLBILL_MYSQL_PASSWORD
}
launcher = "java -Xms512m "
props.each { |key, value| launcher << "-D#{key}=#{value} " }
launcher = "#{launcher} -jar #{killbill_war}"

ohai "Installation successful!"
puts "Now type: #{launcher}"
puts "You can then verify killbill is running by checking its healthcheck: curl -v http://127.0.0.1:8080/1.0/healthcheck"