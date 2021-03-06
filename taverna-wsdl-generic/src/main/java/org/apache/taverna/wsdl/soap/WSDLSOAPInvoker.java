/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.apache.taverna.wsdl.soap;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.activation.DataHandler;
import javax.wsdl.WSDLException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import org.apache.taverna.wsdl.parser.UnknownOperationException;
import org.apache.taverna.wsdl.parser.WSDLParser;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

/**
 * Invoke SOAP based webservices
 * 
 * @author Stuart Owen
 * 
 */
@SuppressWarnings("unchecked")
public class WSDLSOAPInvoker {

	private static final String ATTACHMENT_LIST = "attachmentList";
	
	private static Logger logger = Logger.getLogger(WSDLSOAPInvoker.class);

	private BodyBuilderFactory bodyBuilderFactory = BodyBuilderFactory.instance();
	private WSDLParser parser;
	private String operationName;
	private List<String> outputNames;
        
        private JaxWSInvoker invoker;
        
	public WSDLSOAPInvoker(WSDLParser parser, String operationName,
			List<String> outputNames) {
            this.parser = parser;
            this.operationName = operationName;
            this.outputNames = outputNames;
    
            invoker = new JaxWSInvoker(parser, null, operationName);
            invoker.setTimeout(getTimeout());
	}
	
        public void setCredentials(String username, String password) {
            invoker.setCredentials(username, password);
        }
        
        public void setWSSSecurity(WSSTokenProfile token) {
            invoker.setWSSSecurity(token);
        }
        
	protected String getOperationName() {
            return operationName;
	}
	
	protected WSDLParser getParser() {
            return parser;
	}

	protected List<String> getOutputNames() {
            return outputNames;
	}
	

	/**
	 * Invokes the webservice with the supplied input Map, and returns a Map
	 * containing the outputs, mapped against their output names.
	 * 
	 * @param inputMap
	 * @return
	 * @throws Exception
	 */
	public Map<String, Object> invoke(Map inputMap) throws Exception {

            SOAPMessage message = makeRequestEnvelope(inputMap);
                
            return invoke(message);
	}

	public SOAPMessage call(SOAPMessage message) throws Exception
        {
            return invoker.call(message);
            
//            String endpoint = parser.getOperationEndpointLocations(operationName).get(0);
//            URL endpointURL = new URL(endpoint);
//
//            String soapAction = parser.getSOAPActionURI(operationName);
//            if (soapAction != null) {
//                MimeHeaders headers = message.getMimeHeaders();
//                headers.setHeader("SOAPAction", soapAction);
//            }
//
//            logger.info("Invoking service with SOAP envelope:\n" + message.getSOAPPart().getEnvelope());
//            
//            SOAPConnectionFactory factory = SOAPConnectionFactory.newInstance();
//            SOAPConnection connection = factory.createConnection();  
//
////		call.setTimeout(getTimeout());
//            return connection.call(message, endpointURL);
        }
        
	/**
	 * Invokes the webservice with the supplied input Map and preconfigured axis call, 
	 * and returns a Map containing the outputs, mapped against their output names.
	 */
	public Map<String, Object> invoke(SOAPMessage message)
			throws Exception {

                SOAPMessage response = call(message);
		
                List<SOAPElement> responseElements = new ArrayList();
                for (Iterator<SOAPElement> iter = response.getSOAPBody().getChildElements(); iter.hasNext();)
                {
                    responseElements.add(iter.next());
                }
		logger.info("Received SOAP response:\n"+response);
                
                Map<String, Object> result;
                if (responseElements.isEmpty()) {
			if (outputNames.size() == 1
					&& outputNames.get(0).equals(ATTACHMENT_LIST)) {
				// Could be axis 2 service with no output (TAV-617)
				result = new HashMap<String, Object>();
			} else {
				throw new IllegalStateException(
						"Missing expected outputs from service");
			}                    
		} else {
			logger.info("SOAP response was:" + response);
			SOAPResponseParser responseParser = 
                                SOAPResponseParserFactory.instance().create(responseElements,
                                                                            getUse(),
							                    getStyle(),
							                    parser.getOperationOutputParameters(operationName));
			result = responseParser.parse(responseElements);
		}

		result.put(ATTACHMENT_LIST, extractAttachments(message));

		return result;
	}

	protected SOAPMessage makeRequestEnvelope(Map inputMap)
			throws UnknownOperationException, IOException, WSDLException,
			ParserConfigurationException, SOAPException, SAXException {
	
            MessageFactory factory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL); // TODO: SOAP version
            SOAPMessage message = factory.createMessage();
//            
//            String soapAction = parser.getSOAPActionURI(operationName);
//            if (soapAction != null) {
//                MimeHeaders headers = message.getMimeHeaders();
//                headers.addHeader("SOAPAction", soapAction);
//            }
//
//            if (username != null && username.length() > 0 && 
//                password != null && password.length() > 0) {
//                String authorization = DatatypeConverter.printBase64Binary((username+":"+password).getBytes());
//                MimeHeaders headers = message.getMimeHeaders();
//                headers.addHeader("Authorization", "Basic " + authorization);
//            }
            
            SOAPEnvelope requestEnv = message.getSOAPPart().getEnvelope();
                
            addSoapHeader(requestEnv);
                
            requestEnv.getBody().addChildElement(makeSoapBody(inputMap));

            return message;
	}

        protected void addSoapHeader(SOAPEnvelope envelope) throws SOAPException
        {
        }

	protected SOAPElement makeSoapBody(Map inputMap)
			throws UnknownOperationException, IOException, WSDLException,
			ParserConfigurationException, SOAPException, SAXException {
		
            BodyBuilder builder = 
                    bodyBuilderFactory.create(parser, operationName, parser.getOperationInputParameters(operationName));
                
            return builder.build(inputMap);
	}

	/**
	 * Reads the property taverna.wsdl.timeout, default to 5 minutes if missing.
	 * 
	 * @return
	 */
	protected Integer getTimeout() {
		int result = 300000;
		String minutesStr = System.getProperty("taverna.wsdl.timeout");

		if (minutesStr == null) {
			// using default of 5 minutes
			return result;
		}
		try {
			int minutes = Integer.parseInt(minutesStr.trim());
			result = minutes * 1000 * 60;
		} catch (NumberFormatException e) {
			logger.error("Non-integer timeout", e);
			return result;
		}
		return result;
	}

	protected String getStyle() throws UnknownOperationException {
		return parser.getStyle(operationName);
	}

	protected String getUse() throws UnknownOperationException {
		return parser.getUse(operationName);
	}	

	/**
	 * Exctracts any attachments that result from invoking the service, and
	 * returns them as a List wrapped within a DataThing
	 * 
	 * @param message
	 * @return
	 * @throws SOAPException
	 * @throws IOException
	 */
	protected List extractAttachments(SOAPMessage message)
			throws SOAPException, IOException {
		List attachmentList = new ArrayList();
		if (message.countAttachments() > 0) {
			for (Iterator i = message.getAttachments(); i
					.hasNext();) {
				AttachmentPart ap = (AttachmentPart) i.next();
				DataHandler dh = ap.getDataHandler();
				BufferedInputStream bis = new BufferedInputStream(dh
						.getInputStream());
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				int c;
				while ((c = bis.read()) != -1) {
					bos.write(c);
				}
				bis.close();
				bos.close();
				String mimeType = dh.getContentType();
				if (mimeType.matches(".*image.*")
						|| mimeType.matches(".*octet.*")
						|| mimeType.matches(".*audio.*")
						|| mimeType.matches(".*application/zip.*")) {
					attachmentList.add(bos.toByteArray());
				} else {
					attachmentList.add(new String(bos.toByteArray()));
				}
			}
		}

		return attachmentList;
	}
}
