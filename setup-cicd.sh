#!/bin/bash

# SmartDoc CI/CD Pipeline Setup Script
# This script automates the setup of the complete CI/CD infrastructure

set -e

echo "=========================================="
echo "SmartDoc CI/CD Pipeline Setup"
echo "=========================================="
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Check prerequisites
check_prerequisites() {
    echo ""
    echo "Checking prerequisites..."
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        exit 1
    fi
    print_success "Docker found"
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed"
        exit 1
    fi
    print_success "Docker Compose found"
    
    if ! command -v kubectl &> /dev/null; then
        print_warning "kubectl is not installed - needed for Kubernetes deployment"
    else
        print_success "kubectl found"
    fi
    
    if ! command -v git &> /dev/null; then
        print_error "Git is not installed"
        exit 1
    fi
    print_success "Git found"
}

# Start CI/CD services
start_services() {
    echo ""
    echo "Starting CI/CD services..."
    
    if [ ! -f "docker-compose.cicd.yaml" ]; then
        print_error "docker-compose.cicd.yaml not found in current directory"
        exit 1
    fi
    
    docker-compose -f docker-compose.cicd.yaml up -d
    
    print_success "Services starting..."
    echo ""
    echo "Waiting for services to be healthy..."
    
    # Wait for PostgreSQL
    echo -n "Waiting for PostgreSQL..."
    for i in {1..30}; do
        if docker-compose -f docker-compose.cicd.yaml exec -T postgres pg_isready -U smartdoc &> /dev/null; then
            print_success " PostgreSQL ready"
            break
        fi
        echo -n "."
        sleep 2
    done
    
    # Wait for SonarQube
    echo -n "Waiting for SonarQube..."
    for i in {1..60}; do
        if curl -s http://localhost:9000/api/system/health | grep -q "UP"; then
            print_success " SonarQube ready"
            break
        fi
        echo -n "."
        sleep 3
    done
    
    # Wait for Jenkins
    echo -n "Waiting for Jenkins..."
    for i in {1..60}; do
        if curl -s http://localhost:8080/api/json &> /dev/null; then
            print_success " Jenkins ready"
            break
        fi
        echo -n "."
        sleep 3
    done
}

# Display service information
display_services() {
    echo ""
    echo "=========================================="
    echo "Services Status"
    echo "=========================================="
    docker-compose -f docker-compose.cicd.yaml ps
}

# Display access information
display_access_info() {
    echo ""
    echo "=========================================="
    echo "Access Information"
    echo "=========================================="
    echo ""
    echo "Jenkins:"
    echo "  URL: http://localhost:8080"
    JENKINS_PASS=$(docker exec smartdoc-jenkins cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo "See logs below")
    echo "  Initial Admin Password: $JENKINS_PASS"
    echo ""
    echo "SonarQube:"
    echo "  URL: http://localhost:9000"
    echo "  Username: admin"
    echo "  Password: admin"
    echo ""
    echo "PostgreSQL:"
    echo "  Host: localhost:5432"
    echo "  Username: smartdoc"
    echo "  Password: smartdoc123"
    echo ""
    echo "Kafka:"
    echo "  Bootstrap Servers: localhost:9092"
    echo ""
}

# Setup Kubernetes namespace
setup_kubernetes() {
    echo ""
    echo "Setting up Kubernetes namespace..."
    
    if ! command -v kubectl &> /dev/null; then
        print_warning "kubectl not available, skipping Kubernetes setup"
        return
    fi
    
    if kubectl cluster-info &> /dev/null; then
        kubectl create namespace smartdoc-dev --dry-run=client -o yaml | kubectl apply -f -
        print_success "Kubernetes namespace 'smartdoc-dev' created/verified"
    else
        print_warning "No Kubernetes cluster detected, skipping setup"
    fi
}

# Create directories
create_directories() {
    echo ""
    echo "Creating required directories..."
    
    mkdir -p k8s
    mkdir -p jenkins-config
    mkdir -p docs
    
    print_success "Directories created"
}

# Main execution
main() {
    check_prerequisites
    create_directories
    start_services
    display_services
    display_access_info
    setup_kubernetes
    
    echo ""
    echo "=========================================="
    echo "Setup Complete!"
    echo "=========================================="
    echo ""
    print_info "Next Steps:"
    echo "1. Access Jenkins at http://localhost:8080"
    echo "2. Complete initial setup with admin password"
    echo "3. Install suggested plugins"
    echo "4. Add credentials (Docker Hub, SonarQube, GitHub)"
    echo "5. Create pipeline job from Jenkinsfile"
    echo "6. Configure GitHub webhook for auto-triggers"
    echo ""
    echo "For detailed setup instructions, see: docs/CICD_SETUP_GUIDE.md"
}

# Run main function
main
