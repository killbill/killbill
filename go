#!/usr/bin/env ruby
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
  elsif Kernel.system "/usr/bin/which  #{cmd} > /dev/null"
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

def read_mysql_pwd
  `stty -echo`
  ohai "Enter your MySQL root password:"
  mysql_pwd = gets.chomp
  `stty echo`
  mysql_pwd
end

def mysql_flush_priviledges(pwd)
  system mysql, "-u", "root", "-p#{pwd}", "-e", "flush privileges" 
end

def mysql_abort_if_db_exists(pwd)
  db_exists = (0 < (%x[mysql -u root -p"#{pwd}"  -N -s -e "select count(schema_name) from information_schema.schemata where schema_name = '#{KILLBILL_MYSQL_DATABASE}'"]).chomp.to_i)
  abort "Database #{KILLBILL_MYSQL_DATABASE} already exists! Cowardly aborting installation, drop it first and re-run this script" if db_exists
end

def mysql_abort_if_user_exists(pwd)
  user_exists = (0 < (%x[mysql -u root -p"#{pwd}" -N -s -e "select count(User) from mysql.user where User = '#{KILLBILL_MYSQL_USER}'"]).chomp.to_i)
  abort "User #{KILLBILL_MYSQL_USER} already exists! Cowardly aborting installation, drop it first and re-run this script" if user_exists
end

def mysql_install_killbill(pwd, ddl)
  system mysql, "-u", "root", "-p#{pwd}", "-e", <<CMD
  create database #{KILLBILL_MYSQL_DATABASE};
  create user #{KILLBILL_MYSQL_USER};
  grant all on #{KILLBILL_MYSQL_DATABASE}.* to #{KILLBILL_MYSQL_USER}@localhost identified by '#{KILLBILL_MYSQL_PASSWORD}';
  flush privileges;
  use #{KILLBILL_MYSQL_DATABASE};
  #{ddl}
CMD
end

####################################################################### script
abort "Don't run this as root!" if Process.uid == 0

# Redirect STDERR to /dev/null to avoid seeing the "Warning: Using a password on the command line interface can be insecure."
$stderr.reopen("/dev/null", "w")

# TODO Pierre versioned schema!
ohai "Downloading the latest DDL schema from github..."
ddl = %x[ruby -e "$(#{curl} -skSfL http://kill-bill.org/schema)"]

ohai "Creating MySQL database #{KILLBILL_MYSQL_DATABASE} and user #{KILLBILL_MYSQL_USER} with password #{KILLBILL_MYSQL_PASSWORD}..."
mysql_pwd=read_mysql_pwd

# Make sure privileges are flushed before...
mysql_flush_priviledges(mysql_pwd)

# Check if killbill database already exists and abort if this is the case
mysql_abort_if_db_exists(mysql_pwd)

# Check if killbill users already exists and abort if this is the case
mysql_abort_if_user_exists(mysql_pwd)

# Install killbill database
mysql_install_killbill(mysql_pwd, ddl)

maven_metadata = (get "http://search.maven.org/solrsearch/select?q=g:%22com.ning.billing%22%20AND%20a:%22killbill-server%22%20AND%20p:%22war%22&rows=20&wt=json")
begin
  require 'json'
  latest_version = JSON.parse(maven_metadata)["response"]["docs"][0]["latestVersion"]
rescue LoadError
  latest_version = maven_metadata.scan(/"latestVersion":"([0-9\.]*)"/).first.first
end

killbill_war = "killbill-server-#{latest_version}-jetty-console.war"

ohai "Downloading #{killbill_war}..."
system curl, "-O", "http://search.maven.org/remotecontent?filepath=com/ning/billing/killbill-server/#{latest_version}/#{killbill_war}"

props = {
  "ANTLR_USE_DIRECT_CLASS_LOADING" => "true",
  "com.ning.billing.analytics.dbi.url" => "jdbc:mysql://127.0.0.1:3306/#{KILLBILL_MYSQL_DATABASE}",
  "com.ning.billing.analytics.dbi.user" => KILLBILL_MYSQL_USER,
  "com.ning.billing.analytics.dbi.password" => KILLBILL_MYSQL_PASSWORD,
  "com.ning.jetty.jdbi.url" => "jdbc:mysql://127.0.0.1:3306/#{KILLBILL_MYSQL_DATABASE}",
  "com.ning.jetty.jdbi.user" => KILLBILL_MYSQL_USER,
  "com.ning.jetty.jdbi.password" => KILLBILL_MYSQL_PASSWORD
}
launcher = "java -server -XX:+UseConcMarkSweepGC -Xms512m -Xmx1024m -XX:MaxPermSize=512m "
props.each { |key, value| launcher << "-D#{key}=#{value} " }
launcher = "#{launcher} -jar #{killbill_war}"

ohai "Installation successful!"
puts "Now type: #{launcher}"
